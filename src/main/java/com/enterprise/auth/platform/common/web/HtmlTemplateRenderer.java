package com.enterprise.auth.platform.common.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class HtmlTemplateRenderer {

    public String render(String templatePath, Map<String, String> values) {
        String template = loadTemplate(templatePath);
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String loadTemplate(String templatePath) {
        ClassPathResource resource = new ClassPathResource(templatePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取 HTML 模板: " + templatePath, ex);
        }
    }
}