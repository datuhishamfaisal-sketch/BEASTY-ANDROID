
package com.beasty.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

public class BusinessHunter {

    public static class BusinessLead {
        public String name;
        public String industry;
        public String location;
        public String contact;
        public String notes;

        public BusinessLead(
            String name,
            String industry,
            String location,
            String contact,
            String notes
        ) {
            this.name = name;
            this.industry = industry;
            this.location = location;
            this.contact = contact;
            this.notes = notes;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            try {
                json.put("name", name);
                json.put("industry", industry);
                json.put("location", location);
                json.put("contact", contact);
                json.put("notes", notes);
            } catch (Exception ignored) {}

            return json;
        }
    }

    private final JSONArray leads = new JSONArray();

    public synchronized void addLead(BusinessLead lead) {
        if (lead != null) {
            leads.put(lead.toJson());
        }
    }

    public synchronized JSONArray getLeads() {
        return leads;
    }

    public synchronized JSONArray search(String keyword) {
        JSONArray results = new JSONArray();

        if (keyword == null) return results;

        String query = keyword.toLowerCase().trim();

        for (int i = 0; i < leads.length(); i++) {
            JSONObject lead = leads.optJSONObject(i);

            if (lead == null) continue;

            String searchable =
                lead.optString("name") + " " +
                lead.optString("industry") + " " +
                lead.optString("location") + " " +
                lead.optString("contact") + " " +
                lead.optString("notes");

            if (searchable.toLowerCase().contains(query)) {
                results.put(lead);
            }
        }

        return results;
    }

    public synchronized boolean removeLead(int index) {
        if (index < 0 || index >= leads.length()) {
            return false;
        }

        JSONArray updated = new JSONArray();

        for (int i = 0; i < leads.length(); i++) {
            if (i != index) {
                updated.put(leads.optJSONObject(i));
            }
        }

        while (leads.length() > 0) {
            leads.remove(0);
        }

        for (int i = 0; i < updated.length(); i++) {
            leads.put(updated.optJSONObject(i));
        }

        return true;
    }

    public synchronized void clear() {
        while (leads.length() > 0) {
            leads.remove(0);
        }
    }
}
