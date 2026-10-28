package com.schoolofadults.dumbbrowser.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.schoolofadults.dumbbrowser.R;
import com.schoolofadults.dumbbrowser.skill.Skill;
import com.schoolofadults.dumbbrowser.skill.SkillRunner;
import com.schoolofadults.dumbbrowser.skill.SkillSync;
import com.schoolofadults.dumbbrowser.skill.SkillViewRenderer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generic skill screen: builds an input form from the skill's inputs, runs
 * the skill, and renders the skill's view template with the extracted values.
 * Fields the sources could not confirm show as "Not available" — never guessed.
 */
public class SkillRunActivity extends AppCompatActivity {

    private Skill skill;
    private LinearLayout formContainer;
    private Button runButton;
    private Button refreshButton;
    private TextView statusView;
    private WebView resultView;
    private FrameLayout runnerContainer;
    private final List<EditText> inputFields = new ArrayList<>();
    private SkillRunner runner;
    private Map<String, String> lastInputs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skill_run);
        formContainer = findViewById(R.id.skill_form);
        runButton = findViewById(R.id.skill_run);
        refreshButton = findViewById(R.id.skill_refresh);
        statusView = findViewById(R.id.skill_status);
        resultView = findViewById(R.id.skill_result);
        runnerContainer = findViewById(R.id.skill_runner_container);

        String skillId = getIntent().getStringExtra("skill_id");
        skill = null;
        for (Skill s : SkillSync.loadCached(this)) {
            if (s.id.equals(skillId)) {
                skill = s;
                break;
            }
        }
        if (skill == null) {
            Toast.makeText(this, R.string.skill_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setTitle(skill.title);
        buildForm();
        runButton.setOnClickListener(v -> run());
        refreshButton.setOnClickListener(v -> {
            if (lastInputs != null) runWith(lastInputs);
        });
        refreshButton.setVisibility(View.GONE);
    }

    private void buildForm() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        for (Skill.Input input : skill.inputs) {
            TextView label = new TextView(this);
            label.setText(input.label);
            label.setTextSize(16);
            formContainer.addView(label);
            EditText edit = new EditText(this);
            edit.setHint(input.hint);
            edit.setSingleLine(true);
            if (input.pattern != null && input.pattern.contains("0-9")
                    && !input.pattern.contains("a-z") && !input.pattern.contains("A-Z")) {
                edit.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
            String saved = prefs.getString("skill_" + skill.id + "_last_" + input.key, "");
            edit.setText(saved);
            formContainer.addView(edit);
            inputFields.add(edit);
        }
    }

    private void run() {
        Map<String, String> inputs = new LinkedHashMap<>();
        for (int i = 0; i < skill.inputs.size(); i++) {
            Skill.Input input = skill.inputs.get(i);
            String value = inputFields.get(i).getText().toString().trim();
            if (input.pattern != null && !input.pattern.isEmpty()) {
                try {
                    if (!Pattern.compile(input.pattern).matcher(value).matches()) {
                        Toast.makeText(this, R.string.skill_invalid_input, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (Exception ignored) {
                }
            } else if (value.isEmpty()) {
                Toast.makeText(this, R.string.skill_invalid_input, Toast.LENGTH_SHORT).show();
                return;
            }
            inputs.put(input.key, value);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            editor.putString("skill_" + skill.id + "_last_" + e.getKey(), e.getValue());
        }
        editor.apply();
        runWith(inputs);
    }

    private void runWith(Map<String, String> inputs) {
        lastInputs = inputs;
        runButton.setEnabled(false);
        refreshButton.setVisibility(View.GONE);
        statusView.setText(R.string.skill_running);
        if (runner != null) runner.destroy();
        runner = new SkillRunner(this, runnerContainer);
        runner.start(skill, inputs, new SkillRunner.Listener() {
            @Override
            public void onProgress(String message) {
                statusView.setText(message);
            }

            @Override
            public void onDone(Map<String, String> values, List<String> sourceTexts,
                               List<String> sourceUrls, String fetchedAt) {
                runButton.setEnabled(true);
                refreshButton.setVisibility(View.VISIBLE);
                statusView.setText(getString(R.string.skill_done, fetchedAt));
                Map<String, Object> templateValues = new HashMap<>();
                for (Map.Entry<String, String> e : values.entrySet()) {
                    templateValues.put(e.getKey(), e.getValue());
                }
                templateValues.put("title", skill.title);
                boolean partial = false;
                for (Skill.Field f : skill.fields) {
                    if (f.required && "UNVERIFIED".equals(values.get(f.key))) partial = true;
                }
                templateValues.put("partial", partial);
                templateValues.put("coaches", buildCoaches(values));
                List<Map<String, Object>> links = new ArrayList<>();
                for (int i = 0; i < sourceUrls.size() && i < skill.sources.size(); i++) {
                    Map<String, Object> link = new HashMap<>();
                    link.put("name", skill.sources.get(i).name);
                    link.put("url", sourceUrls.get(i));
                    links.add(link);
                }
                templateValues.put("sourceLinks", links);
                String html;
                if (skill.viewTemplate != null && !skill.viewTemplate.isEmpty()) {
                    html = SkillViewRenderer.render(skill.viewTemplate, templateValues);
                } else {
                    html = fallbackHtml(values, sourceUrls);
                }
                resultView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            }

            @Override
            public void onError(String message) {
                runButton.setEnabled(true);
                statusView.setText(message);
            }
        });
    }

    /** Coach rail for the extracted train, from the skill's static coach orders. */
    private List<Map<String, Object>> buildCoaches(Map<String, String> values) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            if (skill.extra == null) return out;
            JSONObject orders = skill.extra.optJSONObject("coachOrders");
            if (orders == null) return out;
            String train = values.get("trainNumber");
            if (train == null || train.equals("UNVERIFIED")) return out;
            JSONArray order = orders.optJSONArray(train);
            if (order == null) return out;
            String mine = values.get("coach");
            for (int i = 0; i < order.length(); i++) {
                String code = order.optString(i);
                Map<String, Object> coach = new HashMap<>();
                coach.put("code", code);
                coach.put("position", String.valueOf(i + 1));
                coach.put("active", code.equals(mine));
                out.add(coach);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String fallbackHtml(Map<String, String> values, List<String> sourceUrls) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif'>");
        sb.append("<h2>").append(escape(skill.title)).append("</h2><table>");
        for (Skill.Field f : skill.fields) {
            String v = values.get(f.key);
            if (v == null || v.equals("UNVERIFIED")) v = "Not available";
            sb.append("<tr><td>").append(escape(f.label)).append("</td><td><b>")
                    .append(escape(v)).append("</b></td></tr>");
        }
        sb.append("</table><p>As of ").append(escape(values.get("asOf"))).append("</p>");
        for (String url : sourceUrls) {
            sb.append("<p><a href='").append(escape(url)).append("'>")
                    .append(escape(url)).append("</a></p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Override
    protected void onDestroy() {
        if (runner != null) runner.destroy();
        super.onDestroy();
    }
}
