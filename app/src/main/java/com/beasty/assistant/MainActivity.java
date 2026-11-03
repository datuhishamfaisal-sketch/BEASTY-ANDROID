
package com.beasty.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;

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
    private Button wakeButton;
    private boolean wakeWordEnabled = false;
    private boolean wakeTriggered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (WakeWordService.ACTION_WAKE_DETECTED.equals(intent.getAction())) {
                wakeTriggered = true;
                if (statusText != null) statusText.setText("● HEY BEASTY DETECTED");
                mainHandler.postDelayed(() -> startListening(), 900);
            }
        }
    };
    private Spinner modeSpinner;
    private ScrollView scrollView;
    private TextView statusText;
    private TextView thinkingView;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private boolean voiceEnabled = true;
    private boolean isThinking = false;

    private final ArrayList<JSONObject> history = new ArrayList<>();

    private static final String GEMINI_MODEL = "gemini-3.8-flash";
    private static final String GROQ_MODEL = "openai/gpt-oss-20b";

    private static final int MIC_PERMISSION = 100;

    private final int BG = Color.rgb(8, 13, 27);
    private final int PANEL = Color.rgb(19, 29, 49);
    private final int CYAN = Color.rgb(0, 225, 210);
    private final int WHITE = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        createInterface();
        setupTextToSpeech();
        setupSpeechRecognition();
        registerReceiver(wakeReceiver, new IntentFilter(WakeWordService.ACTION_WAKE_DETECTED), Context.RECEIVER_NOT_EXPORTED);

        addMessage(
                "BEASTY",
                "System online. Saya BEASTY, AI assistant anda. Apa yang boleh saya bantu hari ini?",
                false
        );
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(
            int color,
            int strokeColor,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }

        return drawable;
    }

    private TextView makeText(
            String text,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(BG);

        // HEADER

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(shape(PANEL, CYAN, 18));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView title = makeText("BEASTY", 25, WHITE, true);
        TextView subtitle = makeText(
                "NEXT GENERATION AI",
                10,
                CYAN,
                true
        );

        brand.addView(title);
        brand.addView(subtitle);

        header.addView(
                brand,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setGravity(Gravity.CENTER);
        statusBox.setPadding(dp(10), dp(6), dp(10), dp(6));
        statusBox.setBackground(shape(
                Color.rgb(10, 55, 54),
                CYAN,
                15
        ));

        statusText = makeText("● ONLINE", 11, CYAN, true);
        statusBox.addView(statusText);

        header.addView(statusBox);
        root.addView(header);

        // MODE SELECTOR

        TextView modeLabel = makeText(
                "AI INTELLIGENCE MODE",
                10,
                CYAN,
                true
        );

        modeLabel.setPadding(dp(5), dp(12), 0, dp(5));
        root.addView(modeLabel);

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
        modeSpinner.setBackground(shape(PANEL, CYAN, 12));

        root.addView(
                modeSpinner,
                new LinearLayout.LayoutParams(-1, dp(46))
        );

        // CHAT AREA

        LinearLayout chatPanel = new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        chatPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        chatPanel.setBackground(shape(PANEL, Color.rgb(35, 65, 85), 18));

        TextView chatHeader = makeText(
                "◈  BEASTY CONVERSATION",
                11,
                CYAN,
                true
        );

        chatHeader.setPadding(dp(5), dp(5), dp(5), dp(8));
        chatPanel.addView(chatHeader);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(dp(3), dp(5), dp(3), dp(5));

        scrollView.addView(chatLayout);

        chatPanel.addView(
                scrollView,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        root.addView(
                chatPanel,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        // VOICE CONTROLS

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);
        voiceRow.setGravity(Gravity.CENTER);
        voiceRow.setPadding(0, dp(9), 0, dp(5));

        micButton = createButton("🎙 MIC", CYAN);
        voiceButton = createButton("VOICE ON", Color.rgb(55, 90, 130));

        LinearLayout.LayoutParams voiceParams =
                new LinearLayout.LayoutParams(0, dp(48), 1);

        voiceParams.setMargins(dp(3), 0, dp(3), 0);

        voiceRow.addView(micButton, voiceParams);
        voiceRow.addView(voiceButton, voiceParams);

        root.addView(voiceRow);

        wakeButton = createButton("HEY BEASTY: OFF", Color.rgb(45, 65, 90));
        LinearLayout.LayoutParams wakeParams = new LinearLayout.LayoutParams(-1, dp(46));
        wakeParams.setMargins(dp(3), dp(2), dp(3), dp(5));
        root.addView(wakeButton, wakeParams);
        wakeButton.setOnClickListener(v -> toggleWakeWord());

        // MESSAGE INPUT

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(4), 0, dp(3));

        inputMessage = new EditText(this);
        inputMessage.setHint("Message BEASTY...");
        inputMessage.setHintTextColor(Color.rgb(140, 155, 175));
        inputMessage.setTextColor(WHITE);
        inputMessage.setTextSize(15);
        inputMessage.setPadding(dp(15), dp(10), dp(10), dp(10));
        inputMessage.setMinHeight(dp(52));
        inputMessage.setMaxLines(4);
        inputMessage.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        inputMessage.setBackground(shape(
                Color.rgb(28, 39, 59),
                CYAN,
                16
        ));

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(0, dp(55), 1);

        editParams.setMargins(0, 0, dp(7), 0);

        inputRow.addView(inputMessage, editParams);

        sendButton = createButton("SEND ➤", CYAN);

        inputRow.addView(
                sendButton,
                new LinearLayout.LayoutParams(dp(90), dp(55))
        );

        root.addView(inputRow);

        TextView footer = makeText(
                "BEASTY AI  •  GEMINI + GROQ",
                9,
                Color.rgb(105, 130, 155),
                false
        );

        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(5), 0, 0);
        root.addView(footer);

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

    private Button createButton(String text, int color) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(WHITE);
        button.setTextSize(12);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(shape(
                color,
                color == CYAN ? Color.WHITE : Color.TRANSPARENT,
                16
        ));

        return button;
    }

    private void addMessage(
            String sender,
            String message,
            boolean user
    ) {

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView bubble = makeText(
                sender + "\n\n" + message,
                14,
                WHITE,
                false
        );

        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setBackground(shape(
                user
                        ? Color.rgb(22, 80, 115)
                        : Color.rgb(34, 48, 70),
                user ? Color.rgb(40, 160, 205) : CYAN,
                16
        ));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        params.setMargins(0, dp(5), 0, dp(5));

        container.addView(bubble, params);
        chatLayout.addView(container);

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

        return "Gunakan Smart Mode. Seimbangkan kelajuan, kecerdasan dan jawapan yang lengkap. Jawab dalam bahasa pengguna.";
    }

    private void sendMessage() {

        if (isThinking) return;

        String message = inputMessage.getText().toString().trim();

        if (message.isEmpty()) return;

        inputMessage.setText("");

        addMessage("YOU", message, true);

        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("text", message);
            history.add(userMessage);

        } catch (Exception e) {
            addMessage("BEASTY", "Message history error.", false);
            return;
        }

        isThinking = true;
        sendButton.setEnabled(false);
        sendButton.setText("WAIT...");

        if (statusText != null) {
            statusText.setText("● THINKING");
        }

        thinkingView = makeText(
                "◈ BEASTY IS THINKING...",
                13,
                CYAN,
                true
        );

        thinkingView.setPadding(dp(12), dp(15), dp(12), dp(15));
        chatLayout.addView(thinkingView);

        scrollView.post(() ->
                scrollView.fullScroll(View.FOCUS_DOWN)
        );

        final String instruction = getModeInstruction();

        final JSONArray historySnapshot = new JSONArray();

        try {
            for (JSONObject item : history) {
                historySnapshot.put(new JSONObject(item.toString()));
            }
        } catch (Exception ignored) {
        }

        new Thread(() -> {

            String answer = "";
            String geminiError = "";
            String groqError = "";

            try {
                answer = callGemini(historySnapshot, instruction);

            } catch (Exception e) {
                geminiError = e.getMessage();
            }

            if (answer == null || answer.trim().isEmpty()) {

                try {
                    answer = callGroq(historySnapshot, instruction);

                } catch (Exception e) {
                    groqError = e.getMessage();
                }
            }

            if (answer == null || answer.trim().isEmpty()) {

                answer = "BEASTY CONNECTION ERROR\n\n"
                        + "GEMINI: " + geminiError
                        + "\n\nGROQ: " + groqError;
            }

            final String finalAnswer = answer;

            runOnUiThread(() -> {

                isThinking = false;
                sendButton.setEnabled(true);
                sendButton.setText("SEND ➤");

                if (statusText != null) {
                    statusText.setText("● ONLINE");
                }

                if (thinkingView != null) {
                    chatLayout.removeView(thinkingView);
                    thinkingView = null;
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

    private String callGemini(
            JSONArray contents,
            String instruction
    ) throws Exception {

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
                        + instruction
        );

        systemParts.put(systemText);
        systemInstruction.put("parts", systemParts);

        body.put("systemInstruction", systemInstruction);
        body.put("contents", contents);

        JSONObject config = new JSONObject();
        config.put("temperature", 0.7);
        config.put("maxOutputTokens", 4096);

        body.put("generationConfig", config);

        String response = makeRequest(endpoint, body, null);

        JSONObject json = new JSONObject(response);

        JSONArray candidates = json.getJSONArray("candidates");

        if (candidates.length() == 0) {
            throw new Exception("Gemini returned no candidates");
        }

        JSONObject content = candidates
                .getJSONObject(0)
                .getJSONObject("content");

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

    private String callGroq(
            JSONArray historySnapshot,
            String instruction
    ) throws Exception {

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
                        + instruction
        );

        messages.put(system);

        for (int i = 0; i < historySnapshot.length(); i++) {

            JSONObject item = historySnapshot.getJSONObject(i);

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

        String response = makeRequest(endpoint, body, apiKey);

        JSONObject json = new JSONObject(response);

        JSONArray choices = json.getJSONArray("choices");

        if (choices.length() == 0) {
            throw new Exception("Groq returned no choices");
        }

        String result = choices
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "");

        if (result.trim().isEmpty()) {
            throw new Exception("Groq returned empty response");
        }

        return result;
    }

    private String makeRequest(
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

            if (apiKey != null) {
                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + apiKey
                );
            }

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

        if (stream == null) return "";

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
                return json.getJSONObject("error")
                        .optString("message", response);
            }

        } catch (Exception ignored) {
        }

        return response == null || response.isEmpty()
                ? "No error details returned"
                : response;
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
                        micButton.setText("🎙 MIC")
                );
            }

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    micButton.setText("🎙 MIC");
                    Toast.makeText(MainActivity.this,
                            "Voice recognition error: " + error,
                            Toast.LENGTH_SHORT).show();
                });
                if (wakeTriggered) {
                    wakeTriggered = false;
                    mainHandler.postDelayed(() -> {
                        if (wakeWordEnabled) startWakeWordService();
                    }, 1400);
                }
            }

            @Override
            public void onResults(Bundle results) {

                ArrayList<String> matches =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                if (matches != null && !matches.isEmpty()) {

                    runOnUiThread(() -> {
                        inputMessage.setText(matches.get(0));
                        inputMessage.setSelection(inputMessage.getText().length());
                        if (wakeTriggered) {
                            wakeTriggered = false;
                            sendMessage();
                            mainHandler.postDelayed(() -> {
                                if (wakeWordEnabled) startWakeWordService();
                            }, 1800);
                        }
                    });
                }

                runOnUiThread(() ->
                        micButton.setText("🎙 MIC")
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

    private void toggleWakeWord() {
        if (wakeWordEnabled) {
            Intent stop = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_STOP);
            startService(stop);
            wakeWordEnabled = false;
            wakeButton.setText("HEY BEASTY: OFF");
            wakeButton.setBackground(shape(Color.rgb(45, 65, 90), Color.TRANSPARENT, 16));
            if (statusText != null) statusText.setText("● ONLINE");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            return;
        }
        startWakeWordService();
    }

    private void startWakeWordService() {
        try {
            Intent start = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_START);
            startForegroundService(start);
            wakeWordEnabled = true;
            if (wakeButton != null) {
                wakeButton.setText("HEY BEASTY: ON • LISTENING");
                wakeButton.setBackground(shape(Color.rgb(10, 75, 70), CYAN, 16));
            }
            if (statusText != null) statusText.setText("● LISTENING");
        } catch (Exception e) {
            wakeWordEnabled = false;
            Toast.makeText(this, "Wake word could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                    MIC_PERMISSION
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

        try {
            speechRecognizer.startListening(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Cannot start microphone",
                    Toast.LENGTH_SHORT
            ).show();
        }
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

        if (requestCode == MIC_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
        if (requestCode == 101 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordService();
        }
    }

    @Override
    protected void onDestroy() {

        try { unregisterReceiver(wakeReceiver); } catch (Exception ignored) {}
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
