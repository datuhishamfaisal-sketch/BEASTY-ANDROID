package com.beasty.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout chatLayout;
    private EditText inputMessage;
    private Button sendButton;
    private Button micButton;
    private Button voiceButton;
    private Spinner modeSpinner;
    private ScrollView scrollView;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private boolean voiceEnabled = true;
    private boolean isThinking = false;

    private final ArrayList<JSONObject> history = new ArrayList<>();

    private static final String GEMINI_MODEL = "gemini-3.8-flash";
    private static final String GROQ_MODEL = "openai/gpt-oss-20b";

    private String lastApiError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();
        setupTextToSpeech();
        setupSpeechRecognition();

        addMessage("BEASTY",
                "Hai! Saya BEASTY. Saya sedia membantu. Apa yang boleh saya bantu hari ini?",
                false);
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 17, 28));
        root.setPadding(16, 16, 16, 12);

        TextView title = new TextView(this);
        title.setText("BEASTY");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(8, 12, 8, 4);

        TextView subtitle = new TextView(this);
        subtitle.setText("YOUR AI ASSISTANT");
        subtitle.setTextSize(11);
        subtitle.setTextColor(Color.rgb(0, 210, 180));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(4, 0, 4, 14);

        root.addView(title);
        root.addView(subtitle);

        modeSpinner = new Spinner(this);

        String[] modes = {
                "Smart Mode",
                "Fast Mode",
                "Deep Mode"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                modes
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        modeSpinner.setAdapter(adapter);

        root.addView(modeSpinner);

        scrollView = new ScrollView(this);

        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(4, 12, 4, 12);

        scrollView.addView(chatLayout);

        LinearLayout.LayoutParams chatParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        chatParams.topMargin = 8;
        chatParams.bottomMargin = 8;

        root.addView(scrollView, chatParams);

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);

        micButton = createButton("MIC");
        voiceButton = createButton("VOICE ON");

        voiceRow.addView(
                micButton,
                new LinearLayout.LayoutParams(0, 48, 1)
        );

        voiceRow.addView(
                voiceButton,
                new LinearLayout.LayoutParams(0, 48, 1)
        );

        root.addView(voiceRow);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, 8, 0, 0);

        inputMessage = new EditText(this);
        inputMessage.setSingleLine(false);
        inputMessage.setHint("Taip mesej...");
        inputMessage.setTextColor(Color.WHITE);
        inputMessage.setHintTextColor(Color.GRAY);
        inputMessage.setTextSize(15);
        inputMessage.setPadding(14, 8, 14, 8);

        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.rgb(31, 39, 56));
        inputBackground.setCornerRadius(18);
        inputMessage.setBackground(inputBackground);

        sendButton = createButton("SEND");

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(0, 55, 1);

        inputParams.rightMargin = 8;

        inputRow.addView(inputMessage, inputParams);

        inputRow.addView(
                sendButton,
                new LinearLayout.LayoutParams(90, 55)
        );

        root.addView(inputRow);

        setContentView(root);

        sendButton.setOnClickListener(v -> sendMessage());

        voiceButton.setOnClickListener(v -> {
            voiceEnabled = !voiceEnabled;

            voiceButton.setText(
                    voiceEnabled ? "VOICE ON" : "VOICE OFF"
            );

            if (!voiceEnabled && textToSpeech != null) {
                textToSpeech.stop();
            }
        });

        micButton.setOnClickListener(v -> startListening());
    }

    private Button createButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(null, Typeface.BOLD);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(0, 150, 140));
        background.setCornerRadius(18);

        button.setBackground(background);

        return button;
    }

    private void addMessage(String sender, String message, boolean user) {

        TextView bubble = new TextView(this);

        bubble.setText(sender + "\n\n" + message);
        bubble.setTextSize(15);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(16, 12, 16, 12);

        GradientDrawable background = new GradientDrawable();

        if (user) {
            background.setColor(Color.rgb(30, 91, 130));
        } else {
            background.setColor(Color.rgb(38, 48, 65));
        }

        background.setCornerRadius(18);
        bubble.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 6, 0, 6);

        chatLayout.addView(bubble, params);

        scrollView.post(() ->
                scrollView.fullScroll(View.FOCUS_DOWN)
        );
    }

    private String getModeInstruction() {

        String mode = modeSpinner.getSelectedItem().toString();

        if (mode.equals("Fast Mode")) {
            return "Jawab secara ringkas, tepat dan terus kepada perkara penting.";
        }

        if (mode.equals("Deep Mode")) {
            return "Berikan analisis mendalam, terperinci, tersusun dan lengkap. Terangkan langkah demi langkah.";
        }

        return "Gunakan Smart Mode. Seimbangkan kelajuan, kecerdasan dan jawapan yang lengkap. Jawab dalam bahasa yang digunakan oleh pengguna.";
    }

    private void sendMessage() {

        if (isThinking) {
            return;
        }

        String message = inputMessage.getText().toString().trim();

        if (message.isEmpty()) {
            return;
        }

        inputMessage.setText("");

        addMessage("YOU", message, true);

        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("text", message);

            history.add(userMessage);

        } catch (Exception e) {
            addMessage("BEASTY", "Ralat menyimpan mesej.", false);
            return;
        }

        isThinking = true;
        sendButton.setEnabled(false);

        addMessage("BEASTY", "Thinking...", false);

        new Thread(() -> {

            String answer = "";

            try {
                answer = callGemini();

            } catch (Exception e) {
                lastApiError = e.getMessage();
            }

            if (answer == null || answer.isEmpty()) {

                String geminiError = lastApiError;

                try {
                    answer = callGroq();

                } catch (Exception e) {
                    lastApiError = e.getMessage();
                }

                if (answer == null || answer.isEmpty()) {
                    String groqError = lastApiError;

                    answer = "BEASTY CONNECTION ERROR\n\n"
                            + "GEMINI: " + geminiError
                            + "\n\nGROQ: " + groqError;
                }
            }

            final String finalAnswer = answer;

            runOnUiThread(() -> {

                isThinking = false;
                sendButton.setEnabled(true);

                int lastIndex = chatLayout.getChildCount() - 1;

                if (lastIndex >= 0) {
                    chatLayout.removeViewAt(lastIndex);
                }

                addMessage("BEASTY", finalAnswer, false);

                if (!finalAnswer.contains("BEASTY CONNECTION ERROR")) {

                    try {
                        JSONObject aiMessage = new JSONObject();
                        aiMessage.put("role", "assistant");
                        aiMessage.put("text", finalAnswer);

                        history.add(aiMessage);

                    } catch (Exception ignored) {
                    }

                    speak(finalAnswer);
                }
            });

        }).start();
    }

    private String callGemini() throws Exception {

        String apiKey = BuildConfig.GEMINI_API_KEY;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key missing");
        }

        String endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + GEMINI_MODEL
                        + ":generateContent?key="
                        + URLEncoder.encode(apiKey, "UTF-8");

        JSONObject body = new JSONObject();

        JSONObject systemInstruction = new JSONObject();
        JSONArray systemParts = new JSONArray();

        JSONObject systemText = new JSONObject();

        systemText.put(
                "text",
                "You are BEASTY, an intelligent AI assistant. "
                        + "Be helpful, accurate and friendly. "
                        + "Answer using the user's language. "
                        + getModeInstruction()
        );

        systemParts.put(systemText);
        systemInstruction.put("parts", systemParts);

        body.put("systemInstruction", systemInstruction);

        JSONArray contents = new JSONArray();

        for (JSONObject item : history) {

            JSONObject content = new JSONObject();

            String role = item.getString("role");

            content.put(
                    "role",
                    role.equals("assistant") ? "model" : "user"
            );

            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", item.getString("text"));

            parts.put(part);
            content.put("parts", parts);

            contents.put(content);
        }

        body.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 4096);

        body.put("generationConfig", generationConfig);

        String response = makeRequest(endpoint, body);

        JSONObject json = new JSONObject(response);

        JSONArray candidates = json.getJSONArray("candidates");

        JSONObject candidate = candidates.getJSONObject(0);

        JSONObject content = candidate.getJSONObject("content");

        JSONArray parts = content.getJSONArray("parts");

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length(); i++) {
            result.append(parts.getJSONObject(i).optString("text", ""));
        }

        if (result.toString().trim().isEmpty()) {
            throw new Exception("Gemini returned empty response");
        }

        return result.toString();
    }

    private String callGroq() throws Exception {

        String apiKey = BuildConfig.GROQ_API_KEY;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Groq API key missing");
        }

        String endpoint =
                "https://api.groq.com/openai/v1/chat/completions";

        JSONObject body = new JSONObject();

        body.put("model", GROQ_MODEL);
        body.put("temperature", 0.7);
        body.put("max_tokens", 4096);

        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();

        system.put("role", "system");

        system.put(
                "content",
                "You are BEASTY, an intelligent AI assistant. "
                        + "Be helpful, accurate and friendly. "
                        + "Answer using the user's language. "
                        + getModeInstruction()
        );

        messages.put(system);

        for (JSONObject item : history) {

            JSONObject message = new JSONObject();

            String role = item.getString("role");

            message.put(
                    "role",
                    role.equals("assistant") ? "assistant" : "user"
            );

            message.put("content", item.getString("text"));

            messages.put(message);
        }

        body.put("messages", messages);

        String response = makeGroqRequest(endpoint, body, apiKey);

        JSONObject json = new JSONObject(response);

        JSONArray choices = json.getJSONArray("choices");

        JSONObject choice = choices.getJSONObject(0);

        JSONObject message = choice.getJSONObject("message");

        String result = message.optString("content", "");

        if (result.trim().isEmpty()) {
            throw new Exception("Groq returned empty response");
        }

        return result;
    }

    private String makeRequest(
            String endpoint,
            JSONObject body
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(endpoint);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);

            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }

            int code = connection.getResponseCode();

            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            if (code < 200 || code >= 300) {
                throw new Exception(
                        "HTTP " + code + ": " + extractApiError(response)
                );
            }

            return response;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String makeGroqRequest(
            String endpoint,
            JSONObject body,
            String apiKey
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(endpoint);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);

            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + apiKey
            );

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }

            int code = connection.getResponseCode();

            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            if (code < 200 || code >= 300) {
                throw new Exception(
                        "HTTP " + code + ": " + extractApiError(response)
                );
            }

            return response;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws Exception {

        if (stream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();

        return result.toString();
    }

    private String extractApiError(String response) {

        try {

            JSONObject json = new JSONObject(response);

            if (json.has("error")) {

                JSONObject error = json.getJSONObject("error");

                return error.optString("message", response);
            }

        } catch (Exception ignored) {
        }

        if (response == null || response.isEmpty()) {
            return "No error details returned";
        }

        return response;
    }

    private void setupTextToSpeech() {

        textToSpeech = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {

                int result = textToSpeech.setLanguage(
                        new Locale("ms", "MY")
                );

                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {

                    textToSpeech.setLanguage(Locale.US);
                }
            }
        });
    }

    private void speak(String text) {

        if (voiceEnabled && textToSpeech != null) {

            textToSpeech.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "BEASTY_SPEECH"
            );
        }
    }

    private void setupSpeechRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() ->
                        micButton.setText("LISTENING...")
                );
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
                runOnUiThread(() ->
                        micButton.setText("MIC")
                );
            }

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    micButton.setText("MIC");
                    Toast.makeText(
                            MainActivity.this,
                            "Voice recognition error",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onResults(Bundle results) {

                ArrayList<String> matches =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                if (matches != null && !matches.isEmpty()) {

                    inputMessage.setText(matches.get(0));
                    inputMessage.setSelection(
                            inputMessage.getText().length()
                    );
                }

                runOnUiThread(() ->
                        micButton.setText("MIC")
                );
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startListening() {

        if (speechRecognizer == null) {
            Toast.makeText(
                    this,
                    "Speech recognition unavailable",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    100
            );

            return;
        }

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
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
        );

        speechRecognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 100
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startListening();
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
