package com.schoolofadults.dumbbrowser.skill;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches skill definitions from the app's own repo only, and caches them
 * on-device. A new or fixed skill file appears in the app on the next sync
 * with no app update; if a fetch fails, the last good cached copy is used.
 *
 * Security rule: skill definitions are fetched ONLY from this repo. Entry
 * file names must be plain file names (no paths, no "..", no URLs).
 */
public class SkillSync {
    private static final String BASE =
            "https://raw.githubusercontent.com/5HU3HENDU/dumb-browser/main/skills/";
    private static final String INDEX_FILE = "index.json";

    public interface Callback {
        void onDone(List<Skill> skills, boolean fromCache);
        void onError(String message);
    }

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), "skills");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String fetchText(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "DumbBrowser-SkillSync/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code + " for " + urlString);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private static void writeFile(File f, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    private static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private static boolean isSafeFileName(String name) {
        return name != null && !name.isEmpty()
                && !name.contains("/") && !name.contains("\\")
                && !name.contains("..") && !name.startsWith("http");
    }

    /** Reads whatever is cached on-device, without network. */
    public static List<Skill> loadCached(Context context) {
        List<Skill> out = new ArrayList<>();
        try {
            File index = new File(dir(context), INDEX_FILE);
            if (!index.exists()) return out;
            JSONArray entries = new JSONArray(readFile(index));
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                String id = e.optString("id", "");
                String file = e.optString("file", "");
                if (!isSafeFileName(id) || !isSafeFileName(file)) continue;
                File md = new File(dir(context), id + ".md");
                if (!md.exists()) continue;
                Skill skill = SkillParser.parse(id, file, readFile(md));
                if (skill == null) continue;
                skill.title = e.optString("title", skill.title);
                File view = new File(dir(context), id + ".view.html");
                if (view.exists()) skill.viewTemplate = readFile(view);
                out.add(skill);
            }
        } catch (Exception e) {
            android.util.Log.e("SkillSync", "loadCached failed", e);
        }
        return out;
    }

    /** Fetches the registry + every skill md + view template, then caches them. */
    public static void sync(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String indexText = fetchText(BASE + INDEX_FILE);
                JSONArray entries = new JSONArray(indexText);
                List<Skill> skills = new ArrayList<>();
                File d = dir(context);
                writeFile(new File(d, INDEX_FILE), indexText);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.optJSONObject(i);
                    if (e == null) continue;
                    String id = e.optString("id", "");
                    String file = e.optString("file", "");
                    if (id.isEmpty() || !isSafeFileName(id) || !isSafeFileName(file)) continue;
                    String md = fetchText(BASE + file);
                    Skill skill = SkillParser.parse(id, file, md);
                    if (skill == null) continue;
                    skill.title = e.optString("title", skill.title);
                    writeFile(new File(d, id + ".md"), md);
                    String viewFile = e.optString("view", id + ".view.html");
                    if (isSafeFileName(viewFile)) {
                        try {
                            String template = fetchText(BASE + viewFile);
                            skill.viewTemplate = template;
                            writeFile(new File(d, id + ".view.html"), template);
                        } catch (Exception ignored) {
                            // Template is optional; the bundled fallback is used instead.
                        }
                    }
                    skills.add(skill);
                }
                main.post(() -> callback.onDone(skills, false));
            } catch (Exception e) {
                List<Skill> cached = loadCached(context);
                if (!cached.isEmpty()) {
                    main.post(() -> callback.onDone(cached, true));
                } else {
                    main.post(() -> callback.onError(e.getMessage()));
                }
            }
        }).start();
    }
}
