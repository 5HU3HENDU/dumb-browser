package com.schoolofadults.dumbbrowser.skill;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny template engine for skill view templates (remote HTML files).
 * Supports {{key}} (HTML-escaped), {{#each list}}...{{/each}} and
 * {{#if key}}...{{/if}}. Values named UNVERIFIED render as "Not available".
 * No code runs from a template: it is plain HTML with placeholders.
 */
public class SkillViewRenderer {

    public static String render(String template, Map<String, Object> values) {
        if (template == null) return "";
        return renderSection(template, values);
    }

    private static String renderSection(String template, Map<String, Object> values) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf("{{", i);
            if (open < 0) {
                out.append(template.substring(i));
                break;
            }
            out.append(template, i, open);
            int close = template.indexOf("}}", open);
            if (close < 0) {
                out.append(template.substring(open));
                break;
            }
            String tag = template.substring(open + 2, close).trim();
            if (tag.startsWith("#each ")) {
                String key = tag.substring(6).trim();
                int end = findEnd(template, close + 2, "each");
                if (end < 0) break;
                String inner = template.substring(close + 2, end);
                List<Map<String, Object>> items = toList(values.get(key));
                for (Map<String, Object> item : items) {
                    Map<String, Object> merged = new HashMap<>(values);
                    merged.putAll(item);
                    out.append(renderSection(inner, merged));
                }
                i = end + "{{/each}}".length();
            } else if (tag.startsWith("#if ")) {
                String key = tag.substring(4).trim();
                int end = findEnd(template, close + 2, "if");
                if (end < 0) break;
                String inner = template.substring(close + 2, end);
                if (isTruthy(values.get(key))) out.append(renderSection(inner, values));
                i = end + "{{/if}}".length();
            } else if (tag.startsWith("/")) {
                i = close + 2;
            } else {
                Object v = values.get(tag);
                out.append(escape(display(v)));
                i = close + 2;
            }
        }
        return out.toString();
    }

    private static int findEnd(String template, int from, String kind) {
        String openTag = "{{#" + kind + " ";
        String closeTag = "{{/" + kind + "}}";
        int depth = 1;
        int i = from;
        while (i < template.length()) {
            int nextOpen = template.indexOf(openTag, i);
            int nextClose = template.indexOf(closeTag, i);
            if (nextClose < 0) return -1;
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                i = nextOpen + openTag.length();
            } else {
                depth--;
                if (depth == 0) return nextClose;
                i = nextClose + closeTag.length();
            }
        }
        return -1;
    }

    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof List) return !((List<?>) v).isEmpty();
        if (v instanceof String) {
            String s = (String) v;
            return !s.isEmpty() && !s.equals("UNVERIFIED");
        }
        return true;
    }

    private static String display(Object v) {
        if (v == null) return "Not available";
        String s = String.valueOf(v);
        if (s.equals("UNVERIFIED")) return "Not available";
        return s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toList(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object item : (List<Object>) v) {
                if (item instanceof Map) out.add((Map<String, Object>) item);
            }
        } else if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Map<String, Object> m = new HashMap<>();
                java.util.Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    m.put(key, o.opt(key));
                }
                out.add(m);
            }
        }
        return out;
    }
}
