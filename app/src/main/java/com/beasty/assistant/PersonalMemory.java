package com.beasty.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Local-only personal memory. Client/business records remain in ClientMemory.
 * Stores durable, non-sensitive facts expressed by the user.
 */
public class PersonalMemory {
    private static final String PREFS = "beasty_personal_memory";
    private static final String DATA = "facts";
    private final SharedPreferences prefs;

    public PersonalMemory(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean rememberFromUserText(String text) {
        if (text == null) return false;
        String clean = text.trim().replaceAll("\\s+", " ");
        if (clean.length() < 8 || clean.length() > 240) return false;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (isSensitive(lower) || !isDurableFact(lower)) return false;

        JSONArray facts = getFacts();
        for (int i = 0; i < facts.length(); i++) {
            if (clean.equalsIgnoreCase(facts.optString(i))) return false;
        }
        facts.put(clean);
        while (facts.length() > 40) facts.remove(0);
        prefs.edit().putString(DATA, facts.toString()).apply();
        return true;
    }

    private boolean isDurableFact(String s) {
        String[] cues = {
            "saya tinggal", "aku tinggal", "saya bekerja", "aku bekerja",
            "saya kerja", "aku kerja", "saya suka", "aku suka",
            "saya lebih suka", "aku lebih suka", "saya mahu", "aku mahu",
            "saya nak", "aku nak", "projek saya", "bisnes saya",
            "perniagaan saya", "nama saya", "saya menggunakan",
            "saya gunakan", "saya sedang membina", "saya sedang buat",
            "matlamat saya", "pilihan saya", "keutamaan saya",
            "i live", "i work", "i prefer", "i like", "i use",
            "my project", "my business", "my goal", "my preference",
            "my name is"
        };
        for (String cue : cues) if (s.contains(cue)) return true;
        return false;
    }

    // Avoid retaining sensitive personal data automatically.
    private boolean isSensitive(String s) {
        String[] blocked = {
            "password", "kata laluan", "otp", "pin bank", "nombor akaun",
            "account number", "kad pengenalan", "identity card", "passport number",
            "penyakit", "diagnosis", "ubat", "rawatan", "medical",
            "agama saya", "kepercayaan agama", "undi", "parti politik",
            "orientasi seksual", "hubungan seks", "jenayah", "criminal"
        };
        for (String word : blocked) if (s.contains(word)) return true;
        return false;
    }

    public synchronized JSONArray getFacts() {
        try { return new JSONArray(prefs.getString(DATA, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public synchronized String getContext() {
        JSONArray facts = getFacts();
        if (facts.length() == 0) return "";
        StringBuilder out = new StringBuilder("\n\nPERSISTENT USER MEMORY (locally stored facts; use only when relevant):");
        for (int i = 0; i < facts.length(); i++) out.append("\n- ").append(facts.optString(i));
        return out.toString();
    }

    public synchronized String handleCommand(String message) {
        if (message == null) return null;
        String s = message.trim().toLowerCase(Locale.ROOT);
        if (s.equals("apa yang kau ingat") || s.equals("apa yang kau ingat tentang saya")
            || s.equals("lihat memori") || s.equals("tunjuk memori")
            || s.equals("show memory") || s.equals("show my memory")) {
            JSONArray facts = getFacts();
            if (facts.length() == 0) return "Belum ada memori peribadi yang disimpan.";
            StringBuilder out = new StringBuilder("Ini memori peribadi yang BEASTY simpan secara lokal:");
            for (int i = 0; i < facts.length(); i++) out.append("\n").append(i + 1).append(". ").append(facts.optString(i));
            return out.toString();
        }
        if (s.equals("lupakan semua memori") || s.equals("padam semua memori")
            || s.equals("forget all memory") || s.equals("delete all memory")) {
            clear();
            return "Semua memori peribadi BEASTY telah dipadam daripada peranti ini.";
        }
        if (s.startsWith("lupakan memori tentang ") || s.startsWith("forget memory about ")) {
            String term = s.startsWith("lupakan memori tentang ")
                ? s.substring("lupakan memori tentang ".length()).trim()
                : s.substring("forget memory about ".length()).trim();
            if (term.isEmpty()) return "Beritahu perkara yang mahu dilupakan.";
            int removed = forget(term);
            return removed > 0 ? "Memori berkaitan " + term + " telah dipadam."
                : "Tiada memori berkaitan " + term + " ditemui.";
        }
        return null;
    }

    public synchronized int forget(String term) {
        JSONArray old = getFacts(), updated = new JSONArray();
        int removed = 0;
        String needle = term.toLowerCase(Locale.ROOT);
        for (int i = 0; i < old.length(); i++) {
            String fact = old.optString(i);
            if (fact.toLowerCase(Locale.ROOT).contains(needle)) removed++;
            else updated.put(fact);
        }
        prefs.edit().putString(DATA, updated.toString()).apply();
        return removed;
    }

    public synchronized void clear() {
        prefs.edit().remove(DATA).apply();
    }
}
