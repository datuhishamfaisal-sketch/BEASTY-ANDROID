
package com.beasty.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClientMemory {

    private static final String PREFS = "beasty_client_memory";
    private static final String DATA = "clients";

    private final SharedPreferences preferences;

    public ClientMemory(Context context) {
        preferences = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        );
    }

    public synchronized boolean saveClient(
        String name, String phone, String notes
    ) {
        try {
            String cleanName = name == null ? "" : name.trim();

            if (cleanName.isEmpty()) return false;

            JSONArray clients = getClients();
            JSONObject client = new JSONObject();

            client.put("name", cleanName);
            client.put("phone", phone == null ? "" : phone.trim());
            client.put("notes", notes == null ? "" : notes.trim());

            boolean updated = false;

            for (int i = 0; i < clients.length(); i++) {
                JSONObject existing = clients.getJSONObject(i);

                if (existing.optString("name")
                    .equalsIgnoreCase(cleanName)) {

                    clients.put(i, client);
                    updated = true;
                    break;
                }
            }

            if (!updated) clients.put(client);

            preferences.edit()
                .putString(DATA, clients.toString())
                .apply();

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized JSONArray getClients() {
        try {
            return new JSONArray(
                preferences.getString(DATA, "[]")
            );
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public synchronized JSONObject findClient(String name) {
        JSONArray clients = getClients();

        for (int i = 0; i < clients.length(); i++) {
            JSONObject client = clients.optJSONObject(i);

            if (client != null &&
                client.optString("name")
                    .equalsIgnoreCase(name.trim())) {
                return client;
            }
        }

        return null;
    }

    public synchronized boolean deleteClient(String name) {
        JSONArray clients = getClients();
        JSONArray updated = new JSONArray();
        boolean deleted = false;

        for (int i = 0; i < clients.length(); i++) {
            JSONObject client = clients.optJSONObject(i);

            if (client == null) continue;

            if (client.optString("name")
                .equalsIgnoreCase(name.trim())) {
                deleted = true;
            } else {
                updated.put(client);
            }
        }

        if (deleted) {
            preferences.edit()
                .putString(DATA, updated.toString())
                .apply();
        }

        return deleted;
    }

    public synchronized void clearAll() {
        preferences.edit().remove(DATA).apply();
    }
}
