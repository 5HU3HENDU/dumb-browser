package com.schoolofadults.dumbbrowser.skill;

import java.util.ArrayList;
import java.util.List;

/**
 * A skill: one instruction file (skills/*.md) plus its machine block.
 * The md is the human-readable spec; the fenced ```skill-json block inside it
 * is what the app actually runs. Editing the md (including that block) changes
 * behaviour without an app update.
 */
public class Skill {
    public String id = "";
    public String title = "";
    public String file = "";
    public String rawMarkdown = "";
    /** Remote HTML template text, null when not fetched yet. */
    public String viewTemplate = null;
    /** Recheck interval in hours for saved trips using this skill. */
    public int refreshHours = 6;

    public List<Input> inputs = new ArrayList<>();
    public List<Source> sources = new ArrayList<>();
    public List<Field> fields = new ArrayList<>();

    /** Static per-train data shipped in the skill, e.g. typical coach orders. */
    public org.json.JSONObject extra = null;

    public static class Input {
        public String key = "";
        public String label = "";
        public String pattern = "";
        public String hint = "";
    }

    public static class Source {
        public String key = "";
        public String name = "";
        /** URL template; {inputKey} placeholders are substituted at run time. */
        public String url = "";
        public List<Step> steps = new ArrayList<>();
    }

    public static class Step {
        /** open is implicit from source.url; supported steps: fill, submit, wait, extract. */
        public String action = "";
        /** For fill: regex matched against input id/name/placeholder/label. */
        public String label = "";
        /** For fill: value template with {inputKey} placeholders. */
        public String value = "";
        /** For wait: fixed delay in ms. */
        public long delayMs = 3000;
        /** For wait: advance early when the page text matches this regex. */
        public String until = "";
        /** For wait: give up after this many ms. */
        public long timeoutMs = 30000;
    }

    public static class Field {
        public String key = "";
        public String label = "";
        public List<String> patterns = new ArrayList<>();
        public boolean required = false;
    }
}
