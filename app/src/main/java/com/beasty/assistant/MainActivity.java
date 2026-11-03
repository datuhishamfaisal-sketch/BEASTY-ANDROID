
package com.beasty.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout messages;
    private EditText input;
    private TextView status;
    private Spinner modeSpinner;
    private TextToSpeech tts;

    private final String[] modes = {
        "Smart Mode", "Fast Mode", "Deep Mode"
    };

    private static final int VOICE_REQUEST = 41;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ms", "MY"));
            }
        });

        addMessage("BEASTY",
            "Hai! Saya BEASTY. Apa yang boleh saya bantu hari ini?");
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(18, 18, 18, 14);
        page.setBackgroundColor(Color.rgb(8, 17, 31));

        TextView title = new TextView(this);
        title.setText("BEASTY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title);

        status = new TextView(this);
        status.setText("AI Assistant • Gemini + Groq");
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4, 8, 4, 12);
        page.addView(status);

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes
        );
        modeSpinner.setAdapter(adapter);
        page.addView(modeSpinner);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(2, 12, 2, 12);
        scroll.addView(messages);

        page.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout entry = new LinearLayout(this);
        entry.setOrientation(LinearLayout.HORIZONTAL);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setHint("Taip mesej...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(70, 194, 168)
            )
        );

        entry.addView(input,
            new LinearLayout.LayoutParams(0, -2, 1));

        Button mic = new Button(this);
        mic.setText("🎙");
        mic.setOnClickListener(v -> startVoice());
        entry.addView(mic);

        Button send = new Button(this);
        send.setText("Hantar");
        send.setOnClickListener(v -> sendMessage());
        entry.addView(send);

        page.addView(entry);

        TextView modules = new TextView(this);
        modules.setText(
            "Client Memory • Business Hunter • Web Creator\n" +
            "App Creator • Video Creator • Trend Hunter"
        );
        modules.setTextColor(Color.LTGRAY);
        modules.setTextSize(11);
        modules.setPadding(2, 12, 2, 2);
        page.addView(modules);

        setContentView(page);
    }

    private void startVoice() {
        try {
            Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ms-MY"
            );
            intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Cakap dengan BEASTY"
            );
            startActivityForResult(intent, VOICE_REQUEST);

        } catch (Exception e) {
            addMessage("BEASTY",
                "Fungsi suara tidak tersedia pada peranti ini.");
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_REQUEST &&
            resultCode == RESULT_OK && data != null) {

            ArrayList<String> results =
                data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                );

            if (results != null && !results.isEmpty()) {
                input.setText(results.get(0));
                sendMessage();
            }
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

            if (answer == null || answer.trim().isEmpty()) {
                answer = askGroq(prompt);
                provider = "Groq";
            }

            final String result =
                (answer == null || answer.trim().isEmpty())
                ? "AI tidak dapat dihubungi. Sila semak internet dan API key."
                : answer;

            final String used =
                (answer == null || answer.trim().isEmpty())
                ? "Offline" : provider;

            handler.post(() -> {
                status.setText("Sedia • " + used);
                addMessage("BEASTY", result);
            });

        }).start();
    }

    private String systemPrompt() {
        int position = modeSpinner.getSelectedItemPosition();
        String mode = modes[position];

        String detail;

        if (mode.equals("Fast Mode")) {
            detail = "Jawab ringkas dan terus kepada soalan.";
        } else if (mode.equals("Deep Mode")) {
            detail = "Berikan analisis mendalam, tersusun dan lengkap.";
        } else {
            detail = "Seimbangkan kepantasan dengan jawapan lengkap dan bijak.";
        }

        return "You are BEASTY, a helpful multilingual AI assistant. " +
            detail +
            " Respond in the same language as the user.";
    }

    private String askGemini(String prompt) {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) return null;

        try {
            URL url = new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.0-flash:generateContent?key=" +
                BuildConfig.GEMINI_API_KEY
            );

            JSONObject part = new JSONObject()
                .put("text", systemPrompt() + "\nUser: " + prompt);

            JSONObject body = new JSONObject()
                .put("contents",
                    new JSONArray().put(
                        new JSONObject().put(
                            "parts", new JSONArray().put(part)
                        )
                    )
                );

            JSONObject response = post(url, body, null);

            return response.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .optString("text", null);

        } catch (Exception e) {
    android.util.Log.e("BEASTY_GEMINI", "Gemini error", e);
    return "GEMINI_ERROR: " + e.getMessage();
}
    

    private String askGroq(String prompt) {
        if (BuildConfig.GROQ_API_KEY.isEmpty()) return null;

        try {
            URL url = new URL(
                "https://api.groq.com/openai/v1/chat/completions"
            );

            JSONArray msgs = new JSONArray()
                .put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt()))
                .put(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));

            JSONObject body = new JSONObject()
                .put("model", "llama-3.3-70b-versatile")
                .put("messages", msgs);

            JSONObject response =
                post(url, body, BuildConfig.GROQ_API_KEY);

            return response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", null);

        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject post(
        URL url, JSONObject body, String bearer
    ) throws Exception {

        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);

        connection.setRequestProperty(
            "Content-Type", "application/json"
        );

        if (bearer != null) {
            connection.setRequestProperty(
                "Authorization", "Bearer " + bearer
            );
        }

        try (OutputStream out = connection.getOutputStream()) {
            out.write(
                body.toString().getBytes(StandardCharsets.UTF_8)
            );
        }

        int code = connection.getResponseCode();

        InputStream stream =
            code >= 200 && code < 300
            ? connection.getInputStream()
            : connection.getErrorStream();

        if (stream == null) {
            connection.disconnect();
            throw new IOException("Tiada respons daripada server.");
        }

        ByteArrayOutputStream bytes =
            new ByteArrayOutputStream();

        try (InputStream in = stream) {
            byte[] buffer = new byte[4096];
            int count;

            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        }

        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP error: " + code);
        }

        return new JSONObject(
            bytes.toString(StandardCharsets.UTF_8)
        );
    }

    private void addMessage(String who, String message) {
        TextView bubble = new TextView(this);

        bubble.setText(who + "\n" + message);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(15);
        bubble.setPadding(14, 12, 14, 12);

        bubble.setBackgroundColor(
            who.equals("ANDA")
            ? 0xFF183B55
            : 0xFF12352F
        );

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 5, 0, 5);
        messages.addView(bubble, params);

        if (who.equals("BEASTY") && tts != null) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "beasty_reply"
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
