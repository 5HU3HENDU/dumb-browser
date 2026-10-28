package com.schoolofadults.dumbbrowser.skill;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parses a skill md file. The runnable definition lives in a fenced
 * ```skill-json block; everything outside it is human documentation.
 * A file without that block is not runnable (shown as read-only).
 */
public class SkillParser {

    public static Skill parse(String id, String file, String markdown) {
        if (markdown == null) return null;
        int start = markdown.indexOf("```skill-json");
        if (start < 0) return null;
        int bodyStart = markdown.indexOf('\n', start);
        if (bodyStart < 0) return null;
        int end = markdown.indexOf("```", bodyStart + 1);
        if (end < 0) return null;
        String json = markdown.substring(bodyStart + 1, end).trim();
        try {
            JSONObject obj = new JSONObject(json);
            Skill skill = new Skill();
            skill.id = obj.optString("id", id);
            skill.title = obj.optString("title", id);
            skill.file = file;
            skill.rawMarkdown = markdown;
            skill.refreshHours = obj.optInt("refreshHours", 6);
            if (obj.has("extra")) skill.extra = obj.optJSONObject("extra");

            JSONArray inputs = obj.optJSONArray("inputs");
            if (inputs != null) {
                for (int i = 0; i < inputs.length(); i++) {
                    JSONObject o = inputs.optJSONObject(i);
                    if (o == null) continue;
                    Skill.Input in = new Skill.Input();
                    in.key = o.optString("key", "");
                    in.label = o.optString("label", in.key);
                    in.pattern = o.optString("pattern", "");
                    in.hint = o.optString("hint", "");
                    skill.inputs.add(in);
                }
            }
            JSONArray sources = obj.optJSONArray("sources");
            if (sources != null) {
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject o = sources.optJSONObject(i);
                    if (o == null) continue;
                    Skill.Source src = new Skill.Source();
                    src.key = o.optString("key", "");
                    src.name = o.optString("name", src.key);
                    src.url = o.optString("url", "");
                    JSONArray steps = o.optJSONArray("steps");
                    if (steps != null) {
                        for (int j = 0; j < steps.length(); j++) {
                            JSONObject s = steps.optJSONObject(j);
                            if (s == null) continue;
                            Skill.Step step = new Skill.Step();
                            step.action = s.optString("action", "");
                            step.label = s.optString("label", "");
                            step.value = s.optString("value", "");
                            step.delayMs = s.optLong("delayMs", 3000);
                            step.until = s.optString("until", "");
                            step.timeoutMs = s.optLong("timeoutMs", 30000);
                            src.steps.add(step);
                        }
                    }
                    skill.sources.add(src);
                }
            }
            JSONArray fields = obj.optJSONArray("fields");
            if (fields != null) {
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject o = fields.optJSONObject(i);
                    if (o == null) continue;
                    Skill.Field f = new Skill.Field();
                    f.key = o.optString("key", "");
                    f.label = o.optString("label", f.key);
                    f.required = o.optBoolean("required", false);
                    JSONArray pats = o.optJSONArray("patterns");
                    if (pats != null) {
                        for (int j = 0; j < pats.length(); j++) {
                            f.patterns.add(pats.optString(j));
                        }
                    }
                    skill.fields.add(f);
                }
            }
            return skill;
        } catch (Exception e) {
            android.util.Log.e("SkillParser", "bad skill-json in " + file, e);
            return null;
        }
    }
}
