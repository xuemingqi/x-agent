package com.x.ai.common.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * 将可索引文档提取成有界纯文本，禁止 XML 外部实体和静默截断。
 */
public final class DocumentTextUtil {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "csv", "json", "log");

    private static final String DOCX_CONTENT_PATH = "word/document.xml";

    private DocumentTextUtil() {
    }

    public static String contentType(String name) {
        return switch (extension(name)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "html", "htm" -> "text/html";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "md", "markdown" -> "text/markdown";
            case "txt", "log" -> "text/plain";
            default -> throw new IllegalArgumentException("Unsupported document format");
        };
    }

    public static String extract(String name, byte[] source, int maxCharacters) throws IOException {
        String extension = extension(name);
        String text;
        if (TEXT_EXTENSIONS.contains(extension)) {
            text = decodeText(source);
        } else {
            text = switch (extension) {
                case "pdf" -> extractPdf(source, maxCharacters);
                case "docx" -> extractDocx(source, maxCharacters);
                case "html", "htm" -> extractHtml(decodeText(source), maxCharacters);
                default -> throw new IOException("Unsupported document format");
            };
        }
        if (text.length() > maxCharacters || text.indexOf('\0') >= 0) {
            throw new IOException("Document text exceeds limit or contains binary content");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "").strip();
        if (normalized.isBlank()) {
            throw new IOException("Document has no extractable text");
        }
        return normalized;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String decodeText(byte[] source) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
    }

    private static String extractPdf(byte[] source, int limit) throws IOException {
        try (PDDocument document = Loader.loadPDF(source); LimitedTextWriter writer = new LimitedTextWriter(limit)) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF does not permit text extraction");
            }
            new PDFTextStripper().writeText(document, writer);
            return writer.toString();
        }
    }

    private static String extractDocx(byte[] source, int limit) throws IOException {
        try (ZipFile archive = ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(source)).get()) {
            var entry = archive.getEntry(DOCX_CONTENT_PATH);
            if (entry == null) throw new IOException("Missing DOCX document content");
            int xmlLimit = Math.multiplyExact(limit, 8);
            if (entry.getSize() > xmlLimit) throw new IOException("DOCX XML exceeds extraction limit");
            try (var input = archive.getInputStream(entry)) {
                byte[] xml = input.readNBytes(xmlLimit + 1);
                if (xml.length > xmlLimit) throw new IOException("DOCX XML exceeds extraction limit");
                return extractWordXml(xml, limit);
            }
        }
    }

    private static String extractWordXml(byte[] xml, int limit) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try (LimitedTextWriter writer = new LimitedTextWriter(limit)) {
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                boolean text = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new IOException("DOCX external entities are forbidden");
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        text = "t".equals(reader.getLocalName());
                        if ("tab".equals(reader.getLocalName())) writer.write("\t");
                        if ("br".equals(reader.getLocalName())) writer.write("\n");
                    } else if (event == XMLStreamConstants.CHARACTERS && text) {
                        writer.write(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if ("p".equals(reader.getLocalName())) writer.write("\n");
                        text = false;
                    }
                }
                return writer.toString();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException error) {
            throw new IOException("Invalid DOCX XML", error);
        }
    }

    private static String extractHtml(String html, int limit) throws IOException {
        try (LimitedTextWriter writer = new LimitedTextWriter(limit)) {
            HtmlTextCallback callback = new HtmlTextCallback(writer);
            new ParserDelegator().parse(new StringReader(html), callback, true);
            callback.throwIfLimitExceeded();
            return writer.toString();
        }
    }
}
