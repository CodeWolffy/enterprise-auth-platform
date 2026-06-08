package com.enterprise.auth.platform.common;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

public final class HtmlSanitizer {

  private static final Safelist ALLOWED =
      Safelist.basicWithImages()
          .addTags(
              "h1", "h2", "h3", "h4", "h5", "h6",
              "pre", "code", "blockquote", "hr",
              "table", "thead", "tbody", "tr", "th", "td", "br", "span",
              "sup", "sub", "del", "s", "u", "em", "strong")
          .addAttributes("span", "style", "class")
          .addAttributes("p", "style", "class")
          .addAttributes("div", "style", "class")
          .addAttributes("table", "style", "class")
          .addAttributes("th", "style", "class", "colspan", "rowspan")
          .addAttributes("td", "style", "class", "colspan", "rowspan")
          .addAttributes("a", "target", "rel", "title")
          .addAttributes("img", "alt", "title", "width", "height", "loading", "decoding")
          .addProtocols("a", "href", "http", "https", "mailto", "tel")
          .addProtocols("img", "src", "http", "https")
          .addEnforcedAttribute("a", "target", "_blank")
          .addEnforcedAttribute("a", "rel", "noopener noreferrer");

  private static final Set<String> ALLOWED_STYLE_PROPERTIES =
      Set.of(
          "color",
          "background-color",
          "text-align",
          "font-weight",
          "font-style",
          "text-decoration",
          "text-decoration-line",
          "vertical-align");

  private static final List<String> FORBIDDEN_ATTRS =
      List.of("onerror", "onload", "onmouseover", "onclick", "onfocus", "onblur", "onchange", "onsubmit", "oninput", "onkeydown", "onkeypress", "onkeyup", "onmouseenter", "onmouseleave", "onmousemove", "onmouseout", "onmouseup", "onmousedown", "ondblclick", "oncontextmenu", "onscroll", "onresize", "onabort", "oncanplay", "oncanplaythrough", "ondurationchange", "onemptied", "onended", "onloadeddata", "onloadedmetadata", "onloadstart", "onpause", "onplay", "onplaying", "onprogress", "onratechange", "onseeked", "onseeking", "onstalled", "onsuspend", "ontimeupdate", "onvolumechange", "onwaiting", "onbeforeunload", "onhashchange", "onpageshow", "onpagehide", "onpopstate", "onerror", "onmessage", "onopen", "onclose", "ononline", "onoffline", "onstorage", "onbeforeprint", "onafterprint", "onanimationstart", "onanimationend", "onanimationiteration", "ontransitionstart", "ontransitionend", "ontransitionrun", "ontransitioncancel", "onpointerdown", "onpointerup", "onpointercancel", "onpointermove", "onpointerover", "onpointerout", "onpointerenter", "onpointerleave", "ongotpointercapture", "onlostpointercapture", "oncopy", "oncut", "onpaste", "onsearch", "onselect", "onwheel", "ondrag", "ondragend", "ondragenter", "ondragleave", "ondragover", "ondragstart", "ondrop", "onshow", "ontoggle");

  private HtmlSanitizer() {}

  public static String clean(String rawHtml) {
    if (rawHtml == null || rawHtml.isBlank()) {
      return "";
    }
    String cleaned = Jsoup.clean(rawHtml, ALLOWED);
    Document doc = Jsoup.parseBodyFragment(cleaned);
    for (Element element : doc.getAllElements()) {
      for (String attr : FORBIDDEN_ATTRS) {
        element.removeAttr(attr);
      }
      String style = element.attr("style");
      if (!style.isBlank()) {
        String safeStyle = sanitizeStyle(style);
        if (safeStyle.isBlank()) {
          element.removeAttr("style");
        } else {
          element.attr("style", safeStyle);
        }
      }
      String href = element.attr("href");
      if (containsUnsafeToken(href)) {
        element.removeAttr("href");
      }
      String src = element.attr("src");
      if (containsUnsafeToken(src)) {
        element.removeAttr("src");
      }
    }
    return doc.body().html();
  }

  private static String sanitizeStyle(String style) {
    StringBuilder builder = new StringBuilder();
    for (String declaration : style.split(";")) {
      String trimmed = declaration.trim();
      int separatorIndex = trimmed.indexOf(':');
      if (separatorIndex <= 0) {
        continue;
      }
      String property = trimmed.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
      String value = trimmed.substring(separatorIndex + 1).trim();
      if (!ALLOWED_STYLE_PROPERTIES.contains(property) || containsUnsafeToken(value) || !isAllowedStyleValue(property, value)) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append("; ");
      }
      builder.append(property).append(": ").append(value);
    }
    return builder.toString();
  }

  private static boolean containsUnsafeToken(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    return normalized.contains("javascript:")
        || normalized.contains("expression(")
        || normalized.contains("url(")
        || normalized.contains("@import");
  }

  private static boolean isAllowedStyleValue(String property, String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return switch (property) {
      case "text-align" -> Set.of("left", "center", "right", "justify").contains(normalized);
      case "font-weight" -> normalized.matches("^(normal|bold|bolder|lighter|[1-9]00)$");
      case "font-style" -> Set.of("normal", "italic", "oblique").contains(normalized);
      case "text-decoration", "text-decoration-line" -> normalized.matches("^[a-z\\s-]+$");
      case "vertical-align" -> Set.of("baseline", "sub", "super", "top", "middle", "bottom", "text-top", "text-bottom").contains(normalized);
      default -> value.matches("(?i)^(#[0-9a-f]{3,8}|rgba?\\([0-9,.%\\s]+\\)|hsla?\\([0-9,.%\\s]+\\)|[a-z-]+)$");
    };
  }
}