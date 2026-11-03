
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
    private boolean restartWakeAfterSpeech = false;
    private volatile boolean continuousConversation = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String text = intent.getStringExtra(WakeWordService.EXTRA_TEXT);
            if (WakeWordService.ACTION_WAKE_DETECTED.equals(action)) {
                wakeTriggered = false;
                continuousConversation = false;
                if (statusText != null) statusText.setText("● HEY JARVIS DETECTED");
            } else if (WakeWordService.ACTION_VOICE_TRANSCRIPT.equals(action)) {
                if (text != null && !text.trim().isEmpty()) addMessage("YOU", text, true);
                if (statusText != null) statusText.setText("● THINKING");
            } else if (WakeWordService.ACTION_VOICE_REPLY.equals(action)) {
                if (text != null && !text.trim().isEmpty()) addMessage("BEASTY", text, false);
                if (statusText != null) statusText.setText("● LISTENING");
            } else if (WakeWordService.ACTION_VOICE_STATUS.equals(action)) {
                if (statusText != null && text != null) statusText.setText("● " + text.toUpperCase(Locale.ROOT));
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

        // Show the last persisted wake-word checkpoint after a previous
        // process death, including native JNI crashes that Java cannot catch.
        try {
            android.content.SharedPreferences diagnostics =
                    getSharedPreferences("beasty_diagnostics", MODE_PRIVATE);
            String lastWakeStage = diagnostics.getString("wake_last_stage", "");
            String lastKeyword = diagnostics.getString("wake_last_keyword", "");
            String lastTokens = diagnostics.getString("wake_last_tokens", "");
            long audioChunks = diagnostics.getLong("wake_audio_chunks", 0L);
            long decodeCount = diagnostics.getLong("wake_decode_count", 0L);
            long diagnosticAt = diagnostics.getLong("wake_diagnostic_at", 0L);
            String lastWakeError = diagnostics.getString("wake_last_error", "");
            String processExitInfo = "";
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    android.app.ActivityManager activityManager =
                            (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    java.util.List<android.app.ApplicationExitInfo> exits =
                            activityManager.getHistoricalProcessExitReasons(getPackageName(), 0, 3);
                    if (exits != null && !exits.isEmpty()) {
                        android.app.ApplicationExitInfo exit = exits.get(0);
                        processExitInfo = "Previous process exit reason: " + exit.getReason()
                                + "\nExit status: " + exit.getStatus()
                                + "\nExit description: " + exit.getDescription();
                        if (Build.VERSION.SDK_INT >= 31) {
                            try (InputStream trace = exit.getTraceInputStream()) {
                                if (trace != null) {
                                    byte[] traceBytes = new byte[4096];
                                    int traceLength = trace.read(traceBytes);
                                    if (traceLength > 0) {
                                        processExitInfo += "\nNative/system trace: "
                                                + new String(traceBytes, 0, traceLength, StandardCharsets.UTF_8);
                                    }
                                }
                            } catch (Exception traceError) {
                                processExitInfo += "\nTrace unavailable: " + traceError.getClass().getSimpleName();
                            }
                        }
                    }
                } catch (Exception exitError) {
                    processExitInfo = "Process exit diagnostic unavailable: "
                            + exitError.getClass().getSimpleName() + ": " + exitError.getMessage();
                }
            }
            if (lastWakeStage != null && !lastWakeStage.isEmpty()) {
                String detail = "Stage: " + lastWakeStage
                        + "\nError: " + lastWakeError
                        + "\n" + processExitInfo
                        + "\nAudio chunks: " + audioChunks
                        + "\nDecoder runs: " + decodeCount
                        + "\nLast keyword: " + lastKeyword
                        + "\nLast decoded tokens: " + lastTokens
                        + "\nDiagnostic time: " + diagnosticAt;
                addMessage("WAKE DIAGNOSTIC", detail, false);
            }
        } catch (Exception ignored) {
        }

        // Optional voice components must never prevent the chat screen from opening.
        try {
            setupTextToSpeech();
        } catch (Exception e) {
            textToSpeech = null;
            android.util.Log.e("BEASTY_STARTUP", "Text-to-speech initialization failed", e);
        }

        try {
            setupSpeechRecognition();
        } catch (Exception e) {
            speechRecognizer = null;
            android.util.Log.e("BEASTY_STARTUP", "Speech recognition initialization failed", e);
        }

        try {
            IntentFilter wakeFilter = new IntentFilter();
            wakeFilter.addAction(WakeWordService.ACTION_WAKE_DETECTED);
            wakeFilter.addAction(WakeWordService.ACTION_VOICE_TRANSCRIPT);
            wakeFilter.addAction(WakeWordService.ACTION_VOICE_REPLY);
            wakeFilter.addAction(WakeWordService.ACTION_VOICE_STATUS);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(wakeReceiver, wakeFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(wakeReceiver, wakeFilter);
            }
        } catch (Exception e) {
            android.util.Log.e("BEASTY_STARTUP", "Wake receiver registration failed", e);
        }

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
        voiceEnabled = getSharedPreferences("beasty_voice", MODE_PRIVATE)
                .getBoolean("voice_enabled", true);
        voiceButton = createButton(voiceEnabled ? "VOICE ON" : "VOICE OFF", Color.rgb(55, 90, 130));

        LinearLayout.LayoutParams voiceParams =
                new LinearLayout.LayoutParams(0, dp(48), 1);

        voiceParams.setMargins(dp(3), 0, dp(3), 0);

        voiceRow.addView(micButton, voiceParams);
        voiceRow.addView(voiceButton, voiceParams);

        root.addView(voiceRow);

        wakeButton = createButton("HEY JARVIS: OFF", Color.rgb(45, 65, 90));
        LinearLayout.LayoutParams wakeParams = new LinearLayout.LayoutParams(-1, dp(46));
        wakeParams.setMargins(dp(3), dp(2), dp(3), dp(5));
        root.addView(wakeButton, wakeParams);
        wakeButton.setOnClickListener(v -> toggleWakeWord());

        // PERSONAL MEMORY BACKUP / RESTORE
        LinearLayout memoryRow = new LinearLayout(this);
        memoryRow.setOrientation(LinearLayout.HORIZONTAL);
        memoryRow.setGravity(Gravity.CENTER);
        memoryRow.setPadding(0, dp(2), 0, dp(4));

        Button backupMemoryButton = createButton("BACKUP MEMORY", Color.rgb(45, 90, 115));
        Button restoreMemoryButton = createButton("RESTORE MEMORY", Color.rgb(55, 90, 130));
        LinearLayout.LayoutParams memoryButtonParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        memoryButtonParams.setMargins(dp(3), 0, dp(3), 0);
        memoryRow.addView(backupMemoryButton, memoryButtonParams);
        memoryRow.addView(restoreMemoryButton, memoryButtonParams);
        root.addView(memoryRow);

        backupMemoryButton.setOnClickListener(v -> chooseMemoryBackupDestination());
        restoreMemoryButton.setOnClickListener(v -> chooseMemoryBackupFile());

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

            getSharedPreferences("beasty_voice", MODE_PRIVATE).edit()
                    .putBoolean("voice_enabled", voiceEnabled).apply();
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

        PersonalMemory personalMemory = new PersonalMemory(this);
        String memoryCommandReply = personalMemory.handleCommand(message);
        if (memoryCommandReply != null) {
            addMessage("BEASTY", memoryCommandReply, false);
            return;
        }
        personalMemory.rememberFromUserText(message);

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

        final String instruction = getModeInstruction()
                + new PersonalMemory(this).getContext();

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
                    if (!voiceEnabled || textToSpeech == null
                            || finalAnswer.contains("BEASTY CONNECTION ERROR")) {
                        scheduleContinuousListening();
                    }
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
                        + " Write in plain natural text only. Never use emojis, emoticons, decorative symbols, bullet symbols, markdown formatting, dashes, hyphens, commas, semicolons or unnecessary special characters. Use simple sentences with normal full stops only."
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
                        + " Write in plain natural text only. Never use emojis, emoticons, decorative symbols, bullet symbols, markdown formatting, dashes, hyphens, commas, semicolons or unnecessary special characters. Use simple sentences with normal full stops only."
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

                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) {
                        if ("BEASTY_SPEECH".equals(utteranceId)) scheduleContinuousListening();
                    }
                    @Override public void onError(String utteranceId) {
                        if ("BEASTY_SPEECH".equals(utteranceId)) scheduleContinuousListening();
                    }
                    @Override public void onStop(String utteranceId, boolean interrupted) {
                        if ("BEASTY_SPEECH".equals(utteranceId)) scheduleContinuousListening();
                    }
                });

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
                wakeTriggered = false;
                if (continuousConversation) scheduleContinuousListening();
                else resumeWakeWordAfterSpeech();
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
                        if (wakeTriggered || continuousConversation) {
                            wakeTriggered = false;
                            sendMessage();
                        }
                    });
                } else {
                    wakeTriggered = false;
                }

                runOnUiThread(() -> micButton.setText("🎙 MIC"));
                if (continuousConversation) {
                    if (matches == null || matches.isEmpty()) scheduleContinuousListening();
                } else {
                    resumeWakeWordAfterSpeech();
                }
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
            continuousConversation = false;
            wakeTriggered = false;
            if (speechRecognizer != null) speechRecognizer.cancel();
            Intent stop = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_STOP);
            startService(stop);
            wakeWordEnabled = false;
            wakeButton.setText("HEY JARVIS: OFF");
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
            getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                    .putString("wake_last_stage", "activity_before_start_foreground_service")
                    .putString("wake_last_error", "")
                    .putLong("wake_last_time", System.currentTimeMillis())
                    .apply();
            Intent start = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_START);
            startForegroundService(start);
            wakeWordEnabled = true;
            if (wakeButton != null) {
                wakeButton.setText("HEY JARVIS: ON • LISTENING");
                wakeButton.setBackground(shape(Color.rgb(10, 75, 70), CYAN, 16));
            }
            if (statusText != null) statusText.setText("● LISTENING");
        } catch (Throwable e) {
            wakeWordEnabled = false;
            String errorText = e.getClass().getName() + ": " + String.valueOf(e.getMessage());
            getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                    .putString("wake_last_stage", "activity_start_service_exception")
                    .putString("wake_last_error", errorText)
                    .putLong("wake_last_time", System.currentTimeMillis())
                    .apply();
            android.util.Log.e("BEASTY_WAKE_DIAG", "Activity could not start wake service", e);
            Toast.makeText(this, "Wake word error: " + errorText, Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleContinuousListening() {
        if (!continuousConversation || isThinking || isFinishing()) return;
        mainHandler.postDelayed(() -> {
            if (continuousConversation && !isThinking && !isFinishing()) startListening();
        }, 900);
    }

    private void startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }

        // Avoid simultaneous microphone capture by Sherpa and Android speech recognition.
        if (wakeWordEnabled) {
            restartWakeAfterSpeech = true;
            Intent pause = new Intent(this, WakeWordService.class)
                    .setAction(WakeWordService.ACTION_PAUSE);
            startService(pause);
            mainHandler.postDelayed(this::startSpeechRecognition, 700);
        } else {
            startSpeechRecognition();
        }
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot start microphone", Toast.LENGTH_SHORT).show();
            resumeWakeWordAfterSpeech();
        }
    }

    private void resumeWakeWordAfterSpeech() {
        if (!wakeWordEnabled) {
            restartWakeAfterSpeech = false;
            return;
        }
        mainHandler.postDelayed(() -> {
            if (wakeWordEnabled) {
                Intent resume = new Intent(this, WakeWordService.class)
                        .setAction(WakeWordService.ACTION_RESUME);
                startService(resume);
            }
            restartWakeAfterSpeech = false;
        }, 1200);
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

    private static final int REQUEST_MEMORY_BACKUP_EXPORT = 7201;
    private static final int REQUEST_MEMORY_BACKUP_IMPORT = 7202;

    private void chooseMemoryBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "BEASTY-memory-backup.json");
        try {
            startActivityForResult(intent, REQUEST_MEMORY_BACKUP_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka pilihan lokasi fail.", Toast.LENGTH_LONG).show();
        }
    }

    private void chooseMemoryBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            startActivityForResult(intent, REQUEST_MEMORY_BACKUP_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka pemilih fail.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        final android.net.Uri uri = data.getData();
        if (requestCode == REQUEST_MEMORY_BACKUP_EXPORT) {
            try {
                PersonalMemory memory = new PersonalMemory(this);
                JSONObject backup = memory.exportBackup();
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new Exception("Tidak dapat menulis fail.");
                    output.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                Toast.makeText(this,
                        "Backup berjaya. " + backup.getJSONArray("facts").length()
                                + " memory disimpan.",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Backup gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_MEMORY_BACKUP_IMPORT) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Pulihkan memory BEASTY?")
                    .setMessage("Memory daripada fail akan digabungkan dengan memory sedia ada. Memory berganda akan dielakkan. Teruskan?")
                    .setNegativeButton("BATAL", null)
                    .setPositiveButton("PULIHKAN", (dialog, which) -> importMemoryBackup(uri))
                    .show();
        }
    }

    private void importMemoryBackup(android.net.Uri uri) {
        try {
            String jsonText;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new Exception("Fail tidak dapat dibaca.");
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (buffer.size() + count > 1024 * 1024) {
                        throw new Exception("Fail backup terlalu besar.");
                    }
                    buffer.write(chunk, 0, count);
                }
                jsonText = buffer.toString("UTF-8");
            }
            int restored = new PersonalMemory(this).restoreBackup(jsonText);
            Toast.makeText(this, "Restore berjaya. " + restored
                    + " memory baharu dipulihkan.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Restore gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
