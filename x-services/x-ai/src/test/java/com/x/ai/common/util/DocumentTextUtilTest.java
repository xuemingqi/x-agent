package com.x.ai.common.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTextUtilTest {

    @Test
    void extractsWordParagraphsWithoutExposingMarkup() throws IOException {
        byte[] source = word("""
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>中文段落</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二段</w:t></w:r></w:p></w:body>
                </w:document>
                """);
        assertEquals("中文段落\n第二段", DocumentTextUtil.extract("guide.docx", source, 100));
    }

    @Test
    void extractsPdfTextForPreviewAndRetrieval() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(20, 100);
                stream.showText("RAG document content");
                stream.endText();
            }
            document.save(output);
            assertEquals("RAG document content", DocumentTextUtil.extract("guide.pdf", output.toByteArray(), 100));
        }
    }

    @Test
    void rejectsXmlExternalEntitiesAndOversizeText() throws IOException {
        byte[] source = word("""
                <!DOCTYPE document [<!ENTITY content SYSTEM "file:///nonexistent-private-file">]>
                <document><p><t>&content;</t></p></document>
                """);
        assertThrows(IOException.class, () -> DocumentTextUtil.extract("guide.docx", source, 100));
        assertThrows(IOException.class, () -> DocumentTextUtil.extract("guide.txt", "12345".getBytes(), 4));
        assertThrows(IOException.class, () -> DocumentTextUtil.extract("guide.txt", new byte[]{(byte) 0xff}, 100));
        assertThrows(IOException.class, () -> DocumentTextUtil.extract("guide.html", "<p>12345</p>".getBytes(), 4));
    }

    @Test
    void htmlPreviewContainsTextInsteadOfExecutableContent() throws IOException {
        String html = "<html><script>alert('secret')</script><style>body{color:red}</style><p>知识&lt;示例&gt;</p></html>";
        assertEquals("知识<示例>", DocumentTextUtil.extract("guide.html", html.getBytes(StandardCharsets.UTF_8), 100));
    }

    private byte[] word(String xml) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
