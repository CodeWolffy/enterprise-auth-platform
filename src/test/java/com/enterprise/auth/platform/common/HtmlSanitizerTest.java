package com.enterprise.auth.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    @Test
    void shouldKeepRichTextHighlightAndStripUnsafeContent() {
        String cleaned = HtmlSanitizer.clean("""
                <p onclick="alert(1)" style="text-align: center; background-image: url(javascript:alert(1))">
                  <mark style="background-color: #fef08a; color: #111827" onmouseover="alert(1)">重点</mark>
                  <a href="javascript:alert(1)">危险链接</a>
                </p>
                """);

        assertThat(cleaned).contains("<mark style=\"background-color: #fef08a; color: #111827\">重点</mark>");
        assertThat(cleaned).contains("style=\"text-align: center\"");
        assertThat(cleaned).doesNotContain("onclick");
        assertThat(cleaned).doesNotContain("onmouseover");
        assertThat(cleaned).doesNotContain("javascript:");
        assertThat(cleaned).doesNotContain("background-image");
    }

    @Test
    void shouldKeepHeadingAlignmentAndStripUnsafeHeadingStyles() {
        String cleaned = HtmlSanitizer.clean("""
                <h1 style="text-align: center; position: fixed">公告标题</h1>
                <h2 style="text-align: right; background-image: url(javascript:alert(1))">副标题</h2>
                """);

        assertThat(cleaned).contains("<h1 style=\"text-align: center\">公告标题</h1>");
        assertThat(cleaned).contains("<h2 style=\"text-align: right\">副标题</h2>");
        assertThat(cleaned).doesNotContain("position");
        assertThat(cleaned).doesNotContain("background-image");
        assertThat(cleaned).doesNotContain("javascript:");
    }
}
