package com.x.ai.skill;

import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.x.ai.common.constants.SkillArchiveConstant.MAX_ENTRIES;
import static com.x.ai.common.constants.SkillArchiveConstant.MAX_EXPANDED_BYTES;
import static com.x.ai.common.constants.SkillArchiveConstant.MAX_PATH_CHARACTERS;
import static com.x.ai.common.constants.SkillArchiveConstant.MAX_TAR_BYTES;
import static com.x.ai.common.constants.SkillArchiveConstant.MAX_UPLOAD_BYTES;

/**
 * Skill 包的有界内存编解码；归档路径始终只作为相对资源名使用。
 */
public final class SkillArchiveCodec {

    private SkillArchiveCodec() {
    }

    public static Map<String, byte[]> read(byte[] bytes, SkillArchiveFormat format) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) {
            throw invalidArchive();
        }
        return switch (format) {
            case ZIP -> readZip(bytes);
            case TAR -> readTar(bytes);
            case TAR_GZIP -> readTar(decompressGzip(bytes));
        };
    }

    /**
     * 文件仓库按原始字节读取，目录也计入条目上限，且不跟随任何链接。
     */
    public static Map<String, byte[]> readDirectory(Path directory) throws IOException {
        if (!Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory()) {
            throw invalidArchive();
        }
        SkillArchiveEntryCollector collector = new SkillArchiveEntryCollector();
        try (var paths = Files.walk(directory)) {
            var iterator = paths.skip(1).iterator();
            while (iterator.hasNext()) {
                readDirectoryEntry(directory, iterator.next(), collector);
            }
        }
        return collector.getFiles();
    }

    public static byte[] write(Map<String, byte[]> files, SkillArchiveFormat format) throws IOException {
        validateFiles(files);
        return switch (format) {
            case ZIP -> writeZip(files);
            case TAR -> writeTar(files, false);
            case TAR_GZIP -> writeTar(files, true);
        };
    }

    public static String validatePath(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_PATH_CHARACTERS || name.startsWith("/")
                || name.contains("\\") || name.contains(":") || name.chars().anyMatch(Character::isISOControl)) {
            throw invalidArchive();
        }
        String path = normalizePath(name);
        if (Arrays.stream(path.split("/", -1)).anyMatch(segment -> segment.isBlank()
                || segment.equals(".") || segment.equals(".."))) {
            throw invalidArchive();
        }
        return path;
    }

    private static String normalizePath(String name) {
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        SkillArchiveEntryCollector collector = new SkillArchiveEntryCollector();
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(bytes);
             ZipFile archive = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            var entries = archive.getEntries();
            while (entries.hasMoreElements()) {
                readZipEntry(archive, entries.nextElement(), collector);
            }
        }
        return collector.getFiles();
    }

    private static void readZipEntry(ZipFile archive, ZipArchiveEntry entry, SkillArchiveEntryCollector collector) throws IOException {
        validateZipEntry(archive, entry);
        try (InputStream input = archive.getInputStream(entry)) {
            byte[] content = collector.add(entry.getName(), entry.isDirectory(), entry.getSize(), input);
            if (content != null) {
                validateChecksum(content, entry.getCrc());
            }
        }
    }

    private static void validateZipEntry(ZipFile archive, ZipArchiveEntry entry) {
        int type = entry.getUnixMode() & UnixStat.FILE_TYPE_FLAG;
        if (!archive.canReadEntryData(entry) || entry.isUnixSymlink()
                || (type != 0 && type != UnixStat.FILE_FLAG && type != UnixStat.DIR_FLAG)) {
            throw invalidArchive();
        }
    }

    private static void validateChecksum(byte[] content, long expectedChecksum) {
        CRC32 checksum = new CRC32();
        checksum.update(content);
        if (checksum.getValue() != expectedChecksum) {
            throw invalidArchive();
        }
    }

    private static Map<String, byte[]> readTar(byte[] bytes) throws IOException {
        SkillArchiveEntryCollector collector = new SkillArchiveEntryCollector();
        try (TarArchiveInputStream archive = new TarArchiveInputStream(new ByteArrayInputStream(bytes))) {
            TarArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                validateTarEntry(archive, entry);
                collector.add(entry.getName(), entry.isDirectory(), entry.getSize(), archive);
            }
        }
        return collector.getFiles();
    }

    private static void validateTarEntry(TarArchiveInputStream archive, TarArchiveEntry entry) {
        if (!entry.isCheckSumOK() || !archive.canReadEntryData(entry) || entry.isSparse()
                || entry.isSymbolicLink() || entry.isLink() || (!entry.isFile() && !entry.isDirectory())) {
            throw invalidArchive();
        }
    }

    /**
     * 先有界解压到流末尾，确保 GZIP 尾部校验也执行，不被 TAR 结束块提前截断。
     */
    private static byte[] decompressGzip(byte[] bytes) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] expanded = input.readNBytes(MAX_TAR_BYTES + 1);
            if (expanded.length > MAX_TAR_BYTES) {
                throw invalidArchive();
            }
            return expanded;
        }
    }

    private static void readDirectoryEntry(Path directory, Path path, SkillArchiveEntryCollector collector) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() && !attributes.isRegularFile()) {
            throw invalidArchive();
        }
        String name = directory.relativize(path).toString().replace('\\', '/');
        boolean directoryEntry = attributes.isDirectory();
        try (InputStream input = directoryEntry ? InputStream.nullInputStream()
                : Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            collector.add(name, directoryEntry, directoryEntry ? 0 : attributes.size(), input);
        }
    }

    private static void validateFiles(Map<String, byte[]> files) {
        if (files.isEmpty() || files.size() > MAX_ENTRIES
                || files.values().stream().mapToLong(content -> content.length).sum() > MAX_EXPANDED_BYTES) {
            throw invalidArchive();
        }
    }

    private static byte[] writeZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(bytes)) {
            writeEntries(archive, files, file -> new ZipArchiveEntry(validatePath(file.getKey())));
        }
        return bytes.toByteArray();
    }

    private static byte[] writeTar(Map<String, byte[]> files, boolean compressed) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = compressed ? new GZIPOutputStream(bytes) : bytes;
             TarArchiveOutputStream archive = new TarArchiveOutputStream(output)) {
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            archive.setAddPaxHeadersForNonAsciiNames(true);
            writeEntries(archive, files, SkillArchiveCodec::tarEntry);
        }
        return bytes.toByteArray();
    }

    private static TarArchiveEntry tarEntry(Map.Entry<String, byte[]> file) {
        TarArchiveEntry entry = new TarArchiveEntry(validatePath(file.getKey()));
        entry.setSize(file.getValue().length);
        return entry;
    }

    private static <E extends ArchiveEntry> void writeEntries(ArchiveOutputStream<E> archive,
            Map<String, byte[]> files, Function<Map.Entry<String, byte[]>, E> entryFactory) throws IOException {
        for (var file : files.entrySet()) {
            archive.putArchiveEntry(entryFactory.apply(file));
            archive.write(file.getValue());
            archive.closeArchiveEntry();
        }
    }

    private static XException invalidArchive() {
        return new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
    }

}
