package com.beasty.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {

    LinearLayout mainLayout, chatLayout;
    EditText input;
    ScrollView scrollView;
    Button sendButton;

    String geminiKey = BuildConfig.GEMINI_API_KEY;
    String groqKey = BuildConfig.GROQ_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.rgb(5, 11, 18));
        mainLayout.setPadding(20, 25, 20, 15);

        TextView title = new TextView(this);
        title.setText("BEASTY AI");
        title.setTextSize(28);
        title.setTextColor(Color.CYAN);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your Personal AI Assistant");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        mainLayout.addView(subtitle);

        scrollView = new ScrollView(this);
        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(5, 20, 5, 20);

        scrollView.addView(chatLayout);

        mainLayout.addView(scrollView,
                new LinearLayout.LayoutParams(
                        -1, 0, 1));

        input = new EditText(this);
        input.setHint("Ask BEASTY anything...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        mainLayout.addView(input);

        sendButton = new Button(this);
        sendButton.setText("SEND");
        mainLayout.addView(sendButton);

        sendButton.setOnClickListener(v -> sendMessage());

        setContentView(mainLayout);

        addMessage("BEASTY",
                "Hello! I am BEASTY. How can I help you today?");
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
        String message = input.getText().toString().trim();

        if (message.isEmpty()) return;

        input.setText("");
        addMessage("YOU", message);

        sendButton.setEnabled(false);
        addMessage("BEASTY", "Thinking...");

        new Thread(() -> {
            String response = askAI(message);

            runOnUiThread(() -> {
                chatLayout.removeViewAt(chatLayout.getChildCount() - 1);
                addMessage("BEASTY", response);
                sendButton.setEnabled(true);
            });
        }).start();
    }

    String askAI(String message) {

        if (geminiKey != null && !geminiKey.isEmpty()) {
            String result = callGemini(message);
            if (result != null && !result.isEmpty()) return result;
        }

        if (groqKey != null && !groqKey.isEmpty()) {
            String result = callGroq(message);
            if (result != null && !result.isEmpty()) return result;
        }

        return "AI connection failed. Please check your API keys and internet connection.";
    }

    String callGemini(String message) {
        try {
            URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                            + geminiKey);

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type", "application/json");

            JSONObject textPart = new JSONObject();
            textPart.put("text", message);

            JSONArray parts = new JSONArray();
            parts.put(textPart);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject body = new JSONObject();
            body.put("contents", contents);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            InputStream stream =
                    connection.getResponseCode() < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            JSONObject json = new JSONObject(response);
            return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

        } catch (Exception e) {
            return null;
        }
    }

    String callGroq(String message) {
        try {
            URL url = new URL(
                    "https://api.groq.com/openai/v1/chat/completions");

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type", "application/json");

            connection.setRequestProperty(
                    "Authorization", "Bearer " + groqKey);

            JSONObject body = new JSONObject();
            body.put("model", "llama-3.3-70b-versatile");

            JSONArray messages = new JSONArray();

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);

            messages.put(userMessage);
            body.put("messages", messages);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            InputStream stream =
                    connection.getResponseCode() < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = readStream(stream);

            JSONObject json = new JSONObject(response);

            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (Exception e) {
            return null;
        }
    }

    String readStream(InputStream stream) throws Exception {
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
}
