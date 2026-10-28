package com.schoolofadults.dumbbrowser.skill;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a skill's sources in hidden background WebViews, then extracts fields
 * with the skill's regexes. No selectors are assumed: filling finds a text
 * field by its id/name/placeholder/label, and submitting clicks a button by
 * its visible text. If a site changes, only the skill file changes.
 *
 * Everything runs on the UI thread (WebView requirement) as a small state
 * machine; nothing here blocks. Results never leave the phone.
 */
public class SkillRunner {
    public interface Listener {
        void onProgress(String message);
        void onDone(Map<String, String> values, List<String> sourceTexts,
                    List<String> sourceUrls, String fetchedAtIst);
        void onError(String message);
    }

    private static final String TEXT_JS =
            "(function(){var b=document.body;return b?b.innerText:document.documentElement.innerText;})();";

    private static final String FILL_JS =
            "(function(){var re=new RegExp(%1$s,'i');var val=%2$s;"
            + "var els=document.querySelectorAll('input,textarea,select');"
            + "for(var i=0;i<els.length;i++){var el=els[i];if(el.type==='hidden'||el.type==='submit'||el.type==='button'||el.type==='checkbox'||el.type==='radio')continue;"
            + "if(el.offsetParent===null&&el.type!=='select')continue;"
            + "var hay=(el.id||'')+' '+(el.name||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'');"
            + "var lab=el.labels&&el.labels.length?el.labels[0].innerText:'';hay+=' '+(lab||'');"
            + "if(re.test(hay)){try{var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
            + "var setter=Object.getOwnPropertyDescriptor(proto,'value').set;setter.call(el,val);"
            + "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));"
            + "return 'ok';}catch(e){el.value=val;return 'ok';}}}return 'miss';})();";

    private static final String SUBMIT_JS =
            "(function(){var pats=[/check.*pnr/i,/get.*status/i,/submit/i,/search/i];"
            + "var cands=[];var btns=document.querySelectorAll('button,input[type=submit],input[type=button],[role=button]');"
            + "for(var i=0;i<btns.length;i++){var b=btns[i];var t=(b.innerText||b.value||'').trim();if(!t)continue;"
            + "if(b.offsetParent===null)continue;cands.push({el:b,txt:t});}"
            + "for(var p=0;p<pats.length;p++){for(var j=0;j<cands.length;j++){if(pats[p].test(cands[j].txt)){cands[j].el.click();return 'clicked:'+cands[j].txt;}}}"
            + "var f=document.querySelector('form');if(f){try{f.submit();return 'form';}catch(e){}}"
            + "return 'miss';})();";

    private final Context context;
    private final ViewGroup container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private Skill skill;
    private Map<String, String> inputs;
    private Listener listener;
    private int sourceIdx;
    private int stepIdx;
    private String currentText = "";
    private final List<String> texts = new ArrayList<>();
    private final List<String> urls = new ArrayList<>();
    private boolean waitingPage;
    private Runnable pageTimeout;
    private boolean destroyed;

    public SkillRunner(Context context, ViewGroup container) {
        this.context = context;
        this.container = container;
    }

    public void start(Skill skill, Map<String, String> inputs, Listener listener) {
        this.skill = skill;
        this.inputs = inputs;
        this.listener = listener;
        webView = new WebView(context);
        webView.setVisibility(android.view.View.INVISIBLE);
        container.addView(webView, new ViewGroup.LayoutParams(1, 1));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setBlockNetworkImage(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (waitingPage) {
                    waitingPage = false;
                    if (pageTimeout != null) handler.removeCallbacks(pageTimeout);
                    handler.postDelayed(() -> nextStep(), 800);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (waitingPage && request.isForMainFrame()) {
                    waitingPage = false;
                    if (pageTimeout != null) handler.removeCallbacks(pageTimeout);
                    handler.post(() -> nextStep());
                }
            }
        });
        sourceIdx = 0;
        runSource();
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            try {
                webView.stopLoading();
                container.removeView(webView);
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
    }

    private String substitute(String template) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private void progress(String msg) {
        if (listener != null) listener.onProgress(msg);
    }

    private void runSource() {
        if (destroyed) return;
        if (sourceIdx >= skill.sources.size()) {
            finish();
            return;
        }
        Skill.Source src = skill.sources.get(sourceIdx);
        stepIdx = 0;
        currentText = "";
        urls.add(src.url);
        progress("Checking " + src.name + "…");
        String url = substitute(src.url);
        waitingPage = true;
        pageTimeout = () -> {
            if (waitingPage) {
                waitingPage = false;
                nextStep();
            }
        };
        handler.postDelayed(pageTimeout, 35000);
        webView.loadUrl(url);
    }

    private void nextStep() {
        if (destroyed) return;
        Skill.Source src = skill.sources.get(sourceIdx);
        if (stepIdx >= src.steps.size()) {
            texts.add(currentText);
            sourceIdx++;
            runSource();
            return;
        }
        Skill.Step step = src.steps.get(stepIdx++);
        switch (step.action) {
            case "fill": {
                String js = String.format(Locale.US, FILL_JS,
                        JSONObject.quote(substitute(step.label)), JSONObject.quote(substitute(step.value)));
                webView.evaluateJavascript(js, v -> nextStep());
                break;
            }
            case "submit": {
                webView.evaluateJavascript(SUBMIT_JS, v -> {
                    waitingPage = true;
                    pageTimeout = () -> {
                        if (waitingPage) {
                            waitingPage = false;
                            nextStep();
                        }
                    };
                    handler.postDelayed(pageTimeout, 35000);
                });
                break;
            }
            case "wait": {
                waitForText(step);
                break;
            }
            case "extract": {
                webView.evaluateJavascript(TEXT_JS, (ValueCallback<String>) value -> {
                    currentText = unquote(value);
                    nextStep();
                });
                break;
            }
            default:
                nextStep();
                break;
        }
    }

    private void waitForText(Skill.Step step) {
        long deadline = System.currentTimeMillis()
                + (step.timeoutMs > 0 ? step.timeoutMs : 30000);
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (destroyed) return;
            String until = substitute(step.until);
            if (until != null && !until.isEmpty()) {
                webView.evaluateJavascript(TEXT_JS, (ValueCallback<String>) value -> {
                    String text = unquote(value);
                    boolean matched = false;
                    try {
                        matched = Pattern.compile(until,
                                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).find();
                    } catch (Exception ignored) {
                    }
                    if (matched) {
                        currentText = text;
                        nextStep();
                    } else if (System.currentTimeMillis() < deadline) {
                        handler.postDelayed(poll[0], 800);
                    } else {
                        currentText = text;
                        nextStep();
                    }
                });
            } else {
                handler.postDelayed(() -> nextStep(), step.delayMs);
            }
        };
        handler.post(poll[0]);
    }

    private static String unquote(String value) {
        if (value == null) return "";
        try {
            Object parsed = new org.json.JSONTokener(value).nextValue();
            if (parsed instanceof String) return (String) parsed;
        } catch (Exception ignored) {
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void finish() {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            values.put(e.getKey(), e.getValue());
        }
        for (Skill.Field field : skill.fields) {
            String found = null;
            for (String text : texts) {
                for (String pat : field.patterns) {
                    try {
                        Matcher m = Pattern.compile(pat,
                                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
                        if (m.find()) {
                            found = m.groupCount() >= 1 ? m.group(1) : m.group(0);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (found != null) break;
            }
            values.put(field.key, found == null ? "UNVERIFIED" : found.trim());
        }
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH);
        fmt.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String fetchedAt = fmt.format(new Date()) + " IST";
        values.put("asOf", fetchedAt);
        if (listener != null) {
            listener.onDone(values, new ArrayList<>(texts), new ArrayList<>(urls), fetchedAt);
        }
    }
}
