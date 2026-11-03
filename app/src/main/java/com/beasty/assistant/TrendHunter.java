package com.beasty.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

public class TrendHunter {

    public static class Trend {
        public String title;
        public String category;
        public String description;
        public String location;
        public String opportunity;
        public String status;

        public Trend(String title, String category,
                     String description, String location) {
            this.title = title;
            this.category = category;
            this.description = description;
            this.location = location;
            this.opportunity = "";
            this.status = "New";
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            try {
                json.put("title", title);
                json.put("category", category);
                json.put("description", description);
                json.put("location", location);
                json.put("opportunity", opportunity);
                json.put("status", status);
            } catch (Exception ignored) {}

            return json;
        }
    }

    private final JSONArray trends = new JSONArray();

    public synchronized boolean addTrend(Trend trend) {
        if (trend == null || trend.title == null
                || trend.title.trim().isEmpty()) {
            return false;
        }

        trends.put(trend.toJson());
        return true;
    }

    public synchronized JSONArray getTrends() {
        JSONArray copy = new JSONArray();

        for (int i = 0; i < trends.length(); i++) {
            copy.put(trends.optJSONObject(i));
        }

        return copy;
    }

    public synchronized JSONArray search(String keyword) {
        JSONArray results = new JSONArray();

        if (keyword == null) return results;

        String query = keyword.toLowerCase().trim();

        for (int i = 0; i < trends.length(); i++) {
            JSONObject item = trends.optJSONObject(i);

            if (item == null) continue;

            String searchable =
                    item.optString("title") + " " +
                    item.optString("category") + " " +
                    item.optString("description") + " " +
                    item.optString("location") + " " +
                    item.optString("opportunity");

            if (searchable.toLowerCase().contains(query)) {
                results.put(item);
            }
        }

        return results;
    }

    public synchronized boolean updateOpportunity(
            int index, String opportunity) {

        JSONObject item = trends.optJSONObject(index);

        if (item == null || opportunity == null) return false;

        try {
            item.put("opportunity", opportunity);
            item.put("status", "Analyzed");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean removeTrend(int index) {
        if (index < 0 || index >= trends.length()) {
            return false;
        }

        JSONArray updated = new JSONArray();

        for (int i = 0; i < trends.length(); i++) {
            if (i != index) {
                updated.put(trends.optJSONObject(i));
            }
        }

        while (trends.length() > 0) {
            trends.remove(0);
        }

        for (int i = 0; i < updated.length(); i++) {
            trends.put(updated.optJSONObject(i));
        }

        return true;
    }

    public synchronized void clear() {
        while (trends.length() > 0) {
            trends.remove(0);
        }
    }
}
