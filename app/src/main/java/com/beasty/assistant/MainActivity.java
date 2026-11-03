
package com.beasty.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.widget.*;

import org.json.*;

import java.net.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    LinearLayout mainLayout, chatLayout;
    EditText input;
    ScrollView scrollView;
    Button sendButton, micButton, voiceButton;
    Spinner modeSpinner;
    TextView statusText;

    String geminiKey = BuildConfig.GEMINI_API_KEY;
    String groqKey = BuildConfig.GROQ_API_KEY;

    String currentMode = "Smart Mode";

    ArrayList<JSONObject> history = new ArrayList<>();

    SpeechRecognizer speechRecognizer;
    TextToSpeech textToSpeech;

    boolean voiceEnabled = true;
    boolean waiting = false;

    TextView thinkingView;

    Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupUI();
        setupVoice();

        addMessage("BEASTY",
                "Hello! I am BEASTY. Your personal AI assistant is ready.");

    }

    void setupUI() {

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.rgb(5, 11, 18));
        mainLayout.setPadding(18, 20, 18, 15);

        TextView title = new TextView(this);
        title.setText("BEASTY AI");
        title.setTextSize(29);
        title.setTextColor(Color.CYAN);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("YOUR PERSONAL AI ASSISTANT");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(11);
        mainLayout.addView(subtitle);

        statusText = new TextView(this);
        statusText.setText("● BEASTY ONLINE");
        statusText.setTextColor(Color.GREEN);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(5, 12, 5, 8);
        mainLayout.addView(statusText);

        modeSpinner = new Spinner(this);

        String[] modes = {
                "Smart Mode",
                "Fast Mode",
                "Deep Mode"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        modes);

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        modeSpinner.setAdapter(adapter);

        mainLayout.addView(modeSpinner);

        modeSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        currentMode = modes[position];

                        statusText.setText(
                                "● BEASTY ONLINE | " + currentMode);
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {
                    }
                });

        scrollView = new ScrollView(this);

        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(5, 15, 5, 15);

        scrollView.addView(chatLayout);

        mainLayout.addView(scrollView,
                new LinearLayout.LayoutParams(
                        -1, 0, 1));

        input = new EditText(this);
        input.setHint("Ask BEASTY anything...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setPadding(15, 12, 15, 12);
        input.setBackgroundColor(Color.rgb(22, 35, 48));

        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        mainLayout.addView(input);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        micButton = new Button(this);
        micButton.setText("MIC");
        buttons.addView(micButton,
                new LinearLayout.LayoutParams(0, -2, 1));

        sendButton = new Button(this);
        sendButton.setText("SEND");
        buttons.addView(sendButton,
                new LinearLayout.LayoutParams(0, -2, 1));

        voiceButton = new Button(this);
        voiceButton.setText("VOICE ON");
        buttons.addView(voiceButton,
                new LinearLayout.LayoutParams(0, -2, 1));

        mainLayout.addView(buttons);

        sendButton.setOnClickListener(v -> sendMessage());

        micButton.setOnClickListener(v -> startListening());

        voiceButton.setOnClickListener(v -> {

            voiceEnabled = !voiceEnabled;

            voiceButton.setText(
                    voiceEnabled ? "VOICE ON" : "VOICE OFF");

            if (!voiceEnabled && textToSpeech != null) {
                textToSpeech.stop();
            }
        });

        setContentView(mainLayout);
    }

    void addMessage(String sender, String message) {

        TextView text = new TextView(this);

        text.setText(sender + "\n" + message);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        text.setPadding(15, 12, 15, 12);
        text.setBackgroundColor(Color.rgb(18, 35, 48));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 8, 0, 8);

        chatLayout.addView(text, params);

        scrollView.post(() ->
                scrollView.fullScroll(View.FOCUS_DOWN));
    }

    void sendMessage() {

        if (waiting) return;

        String message = input.getText().toString().trim();

        if (message.isEmpty()) return;

        input.setText("");

        ((InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(
                        input.getWindowToken(), 0);

        addMessage("YOU", message);

        waiting = true;
        sendButton.setEnabled(false);

        thinkingView = new TextView(this);
        thinkingView.setText("BEASTY\nThinking...");
        thinkingView.setTextColor(Color.CYAN);
        thinkingView.setPadding(15, 12, 15, 12);

        chatLayout.addView(thinkingView);

        scrollView.post(() ->
                scrollView.fullScroll(View.FOCUS_DOWN));

        new Thread(() -> {

            String response = askAI(message);

            runOnUiThread(() -> {

                if (thinkingView != null) {
                    chatLayout.removeView(thinkingView);
                    thinkingView = null;
                }

                addMessage("BEASTY", response);

                if (voiceEnabled && textToSpeech != null) {
                    textToSpeech.speak(
                            response,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "BEASTY_RESPONSE");
                }

                waiting = false;
                sendButton.setEnabled(true);
            });

        }).start();
    }

    String getSystemPrompt() {

        String base =
                "You are BEASTY, an intelligent personal AI assistant. " +
                "Be helpful, accurate, friendly and practical. " +
                "Respond in the same language used by the user. " +
                "If the user speaks Malay, respond in natural Bahasa Melayu.";

        if (currentMode.equals("Fast Mode")) {
            return base +
                    " Fast Mode: Give short, direct answers.";
        }

        if (currentMode.equals("Deep Mode")) {
            return base +
                    " Deep Mode: Provide detailed reasoning, explanations " +
                    "and comprehensive answers.";
        }

        return base +
                " Smart Mode: Balance speed, intelligence and completeness.";
    }

    String askAI(String message) {

        String response = null;

        if (geminiKey != null && !geminiKey.isEmpty()) {
            response = callGemini(message);
        }

        if (response == null &&
                groqKey != null && !groqKey.isEmpty()) {
            response = callGroq(message);
        }

        if (response == null) {
            return "AI connection failed. Please check your API keys and internet connection.";
        }

        try {
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", message);
            history.add(user);

            JSONObject assistant = new JSONObject();
            assistant.put("role", "assistant");
            assistant.put("content", response);
            history.add(assistant);

            while (history.size() > 20) {
                history.remove(0);
            }

        } catch (Exception ignored) {
        }

        return response;
    }

    String callGemini(String message) {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                            + URLEncoder.encode(geminiKey, "UTF-8"));

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type", "application/json");

            JSONArray contents = new JSONArray();

            for (JSONObject item : history) {

                JSONObject content = new JSONObject();

                content.put("role",
                        item.getString("role").equals("assistant")
                                ? "model" : "user");

                JSONObject part = new JSONObject();
                part.put("text", item.getString("content"));

                JSONArray parts = new JSONArray();
                parts.put(part);

                content.put("parts", parts);
                contents.put(content);
            }

            JSONObject current = new JSONObject();
            current.put("role", "user");

            JSONObject part = new JSONObject();
            part.put("text", message);

            JSONArray parts = new JSONArray();
            parts.put(part);

            current.put("parts", parts);
            contents.put(current);

            JSONObject system = new JSONObject();
            system.put("parts",
                    new JSONArray().put(
                            new JSONObject().put(
                                    "text", getSystemPrompt())));

            JSONObject body = new JSONObject();
            body.put("systemInstruction", system);
            body.put("contents", contents);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = connection.getResponseCode();

            InputStream stream = code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            JSONObject json = new JSONObject(response);

            if (code >= 400) return null;

            return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

        } catch (Exception e) {
            return null;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    String callGroq(String message) {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(
                    "https://api.groq.com/openai/v1/chat/completions");

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type", "application/json");

            connection.setRequestProperty(
                    "Authorization", "Bearer " + groqKey);

            JSONObject body = new JSONObject();

            String model = currentMode.equals("Fast Mode")
                    ? "llama-3.3-70b-versatile"
                    : "llama-3.3-70b-versatile";

            body.put("model", model);
            body.put("messages", buildGroqMessages(message));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = connection.getResponseCode();

            InputStream stream = code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            JSONObject json = new JSONObject(response);

            if (code >= 400) return null;

            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (Exception e) {
            return null;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    JSONArray buildGroqMessages(String message)
            throws Exception {

        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", getSystemPrompt());

        messages.put(system);

        for (JSONObject item : history) {
            messages.put(item);
        }

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", message);

        messages.put(user);

        return messages;
    }

    String readStream(InputStream stream) throws Exception {

        if (stream == null) return "";

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"));

        StringBuilder result = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();

        return result.toString();
    }

    void setupVoice() {

        textToSpeech = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(
                        new Locale("ms", "MY"));
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(
                    new RecognitionListener() {

                        @Override
                        public void onReadyForSpeech(Bundle params) {
                            runOnUiThread(() ->
                                    statusText.setText("● LISTENING..."));
                        }

                        @Override
                        public void onBeginningOfSpeech() {
                        }

                        @Override
                        public void onRmsChanged(float rmsdB) {
                        }

                        @Override
                        public void onBufferReceived(byte[] buffer) {
                        }

                        @Override
                        public void onEndOfSpeech() {
                        }

                        @Override
                        public void onError(int error) {
                            runOnUiThread(() ->
                                    statusText.setText("● BEASTY ONLINE"));
                        }

                        @Override
                        public void onResults(Bundle results) {

                            ArrayList<String> matches =
                                    results.getStringArrayList(
                                            SpeechRecognizer.RESULTS_RECOGNITION);

                            if (matches != null && !matches.isEmpty()) {

                                input.setText(matches.get(0));
                                input.setSelection(
                                        input.getText().length());

                                sendMessage();
                            }

                            statusText.setText("● BEASTY ONLINE");
                        }

                        @Override
                        public void onPartialResults(Bundle results) {
                        }

                        @Override
                        public void onEvent(int eventType, Bundle params) {
                        }
                    });
        }
    }

    void startListening() {

        if (speechRecognizer == null) {
            addMessage("BEASTY",
                    "Speech recognition is not available on this device.");
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    101);

            return;
        }

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ms-MY");

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "ms-MY");

        speechRecognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == 101 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startListening();
        } else if (requestCode == 101) {
            addMessage("BEASTY",
                    "Microphone permission is required for voice input.");
        }
    }

    @Override
    protected void onDestroy() {

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}
