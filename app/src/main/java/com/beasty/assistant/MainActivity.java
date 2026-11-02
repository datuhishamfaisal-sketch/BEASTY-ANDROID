package com.beasty.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout messages;
    private EditText input;
    private TextView status, modeLabel;
    private Spinner modeSpinner;
    private TextToSpeech tts;
    private final String[] modes = {"Smart Mode", "Fast Mode", "Deep Mode"};
    private static final int VOICE_REQUEST = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        tts = new TextToSpeech(this, result -> {});
        addMessage("BEASTY", "Hai! Saya BEASTY. Tulis atau tekan mikrofon untuk mula.\\n\\nMasukkan API key Gemini dan/atau Groq dalam GitHub Secrets untuk mengaktifkan AI.");
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(18, 18, 18, 14);
        page.setBackgroundColor(0xFF08111F);

        TextView title = new TextView(this);
        title.setText("BEASTY");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(8, 8, 8, 2);
        title.setTypeface(null, 1);
        page.addView(title);

        status = new TextView(this);
        status.setText("AI Assistant • Gemini + Groq");
        status.setTextColor(0xFF9FB3C8);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4, 2, 4, 12);
        page.addView(status);

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        page.addView(modeSpinner);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(2, 12, 2, 12);
        scroll.addView(messages);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout entry = new LinearLayout(this);
        entry.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this);
        input.setSingleLine(false);
        input.setHint("Taip mesej...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF9FB3C8);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF46C2A8));
        entry.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button mic = new Button(this); mic.setText("🎙");
        mic.setOnClickListener(v -> startVoice());
        entry.addView(mic);
        Button send = new Button(this); send.setText("Hantar");
        send.setOnClickListener(v -> sendMessage());
        entry.addView(send);
        page.addView(entry);

        TextView modules = new TextView(this);
        modules.setText("Modul: Client Memory • Business Hunter • Web Creator • App Creator • Video Creator • Trend Hunter");
        modules.setTextColor(0xFF8FA6BC);
        modules.setTextSize(11);
        modules.setPadding(2, 10, 2, 2);
        page.addView(modules);
        setContentView(page);
    }

    private void startVoice() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Cakap dengan BEASTY");
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) { addMessage("BEASTY", "Fungsi suara tidak tersedia pada peranti ini."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) { input.setText(results.get(0)); sendMessage(); }
        }
    }

    private void sendMessage() {
        String prompt = input.getText().toString().trim();
        if (prompt.isEmpty()) return;
        input.setText("");
        addMessage("ANDA", prompt);
        status.setText("BEASTY sedang berfikir...");
        new Thread(() -> {
            String answer = askGemini(prompt);
            String provider = "Gemini";
            if (answer == null) { answer = askGroq(prompt); provider = "Groq"; }
            final String result = answer == null
                    ? "Saya belum dapat menghubungi AI. Semak sambungan internet dan tetapan GEMINI_API_KEY / GROQ_API_KEY dalam GitHub Secrets."
                    : answer;
            final String used = answer == null ? "Offline" : provider;
            runOnUiThread(() -> {
                status.setText("Sedia • " + used);
                addMessage("BEASTY", result);
            });
        }).start();
    }

    private String systemPrompt() {
        String mode = modes[modeSpinner.getSelectedItemPosition()];
        String detail = mode.equals("Fast Mode") ? "Jawab ringkas dan terus." :
                mode.equals("Deep Mode") ? "Berikan analisis mendalam, tersusun dan lengkap." :
                "Seimbangkan kepantasan dengan jawapan lengkap dan bijak.";
        return "You are BEASTY, a helpful multilingual Android AI assistant. " + detail;
    }

    private String askGemini(String prompt) {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) return null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + BuildConfig.GEMINI_API_KEY);
            JSONObject part = new JSONObject().put("text", systemPrompt() + "\\nUser: " + prompt);
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(part))));
            JSONObject response = post(url, body, null);
            return response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).optString("text", null);
        } catch (Exception e) { return null; }
    }

    private String askGroq(String prompt) {
        if (BuildConfig.GROQ_API_KEY.isEmpty()) return null;
        try {
            URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
            JSONArray msgs = new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", systemPrompt()))
                    .put(new JSONObject().put("role", "user").put("content", prompt));
            JSONObject body = new JSONObject().put("model", "llama-3.3-70b-versatile").put("messages", msgs);
            JSONObject response = post(url, body, BuildConfig.GROQ_API_KEY);
            return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", null);
        } catch (Exception e) { return null; }
    }

    private JSONObject post(URL url, JSONObject body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(20000); c.setReadTimeout(30000);
        c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        InputStream stream = c.getResponseCode() >= 200 && c.getResponseCode() < 300 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = stream) { byte[] b = new byte[4096]; int n; while ((n=in.read(b))!=-1) bytes.write(b,0,n); }
        c.disconnect();
        return new JSONObject(bytes.toString(StandardCharsets.UTF_8));
    }

    private void addMessage(String who, String message) {
        TextView bubble = new TextView(this);
        bubble.setText(who + "\\n" + message);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(15);
        bubble.setPadding(14, 12, 14, 12);
        bubble.setBackgroundColor(who.equals("ANDA") ? 0xFF183B55 : 0xFF12352F);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 5, 0, 5);
        messages.addView(bubble, lp);
        if (who.equals("BEASTY") && tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "beasty_reply");
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
