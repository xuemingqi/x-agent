package com.x.ai.common.util;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import java.io.IOException;
import java.util.Set;

/**
 * HTML 原生解析回调，仅提取可见正文和段落边界。
 */
public class HtmlTextCallback extends HTMLEditorKit.ParserCallback {

    private static final Set<HTML.Tag> PARAGRAPH_TAGS = Set.of(HTML.Tag.P, HTML.Tag.DIV, HTML.Tag.LI,
            HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3, HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.TR);

    private final LimitedTextWriter writer;

    private boolean ignoreText;

    private IOException extractionError;

    public HtmlTextCallback(LimitedTextWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
        if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignoreText = true;
    }

    @Override
    public void handleEndTag(HTML.Tag tag, int position) {
        if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignoreText = false;
        if (PARAGRAPH_TAGS.contains(tag)) append("\n");
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
        if (tag == HTML.Tag.BR) append("\n");
    }

    @Override
    public void handleText(char[] data, int position) {
        if (!ignoreText) append(new String(data));
    }

    public void throwIfLimitExceeded() throws IOException {
        if (extractionError != null) throw extractionError;
    }

    private void append(String text) {
        if (extractionError != null) return;
        try {
            writer.write(text);
        } catch (IOException error) {
            extractionError = error;
        }
    }
}
