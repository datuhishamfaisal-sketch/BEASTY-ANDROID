package com.beasty.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class WebCreator {
    private static final String PREFS = "beasty_web_creator";
    private static final String PROJECTS = "projects";
    private final SharedPreferences preferences;

    public static class WebsiteProject {
        public String name, description, html, css, javascript, status;
        public WebsiteProject(String name, String description) {
            this.name = name == null ? "Website" : name;
            this.description = description == null ? "" : description;
            this.html = ""; this.css = ""; this.javascript = ""; this.status = "Draft";
        }
        public void updateCode(String html, String css, String javascript) {
            this.html = html == null ? "" : html;
            this.css = css == null ? "" : css;
            this.javascript = javascript == null ? "" : javascript;
            this.status = "Ready for preview";
        }
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("name", name); json.put("description", description);
                json.put("html", html); json.put("css", css);
                json.put("javascript", javascript); json.put("status", status);
                json.put("updatedAt", System.currentTimeMillis());
            } catch (Exception ignored) {}
            return json;
        }
    }

    public WebCreator(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public WebsiteProject createProject(String name, String description) {
        if (name == null || name.trim().isEmpty()) return null;
        return new WebsiteProject(name.trim(), description == null ? "" : description.trim());
    }

    public synchronized boolean saveProject(WebsiteProject project) {
        if (project == null || !isReadyToPreview(project)) return false;
        try {
            JSONArray projects = new JSONArray(preferences.getString(PROJECTS, "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < projects.length(); i++) {
                JSONObject item = projects.getJSONObject(i);
                if (!item.optString("name").equalsIgnoreCase(project.name)) updated.put(item);
            }
            updated.put(project.toJson());
            while (updated.length() > 20) {
                JSONArray trimmed = new JSONArray();
                for (int i = updated.length() - 20; i < updated.length(); i++) trimmed.put(updated.get(i));
                updated = trimmed;
            }
            preferences.edit().putString(PROJECTS, updated.toString()).apply();
            return true;
        } catch (Exception e) { return false; }
    }

    public synchronized WebsiteProject getLatestProject() {
        try {
            JSONArray projects = new JSONArray(preferences.getString(PROJECTS, "[]"));
            if (projects.length() == 0) return null;
            return fromJson(projects.getJSONObject(projects.length() - 1));
        } catch (Exception e) { return null; }
    }

    public synchronized JSONArray listProjects() {
        try { return new JSONArray(preferences.getString(PROJECTS, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public synchronized boolean deleteProject(String name) {
        try {
            JSONArray projects = new JSONArray(preferences.getString(PROJECTS, "[]"));
            JSONArray updated = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < projects.length(); i++) {
                JSONObject item = projects.getJSONObject(i);
                if (item.optString("name").equalsIgnoreCase(name == null ? "" : name.trim())) removed = true;
                else updated.put(item);
            }
            preferences.edit().putString(PROJECTS, updated.toString()).apply();
            return removed;
        } catch (Exception e) { return false; }
    }

    public WebsiteProject parseAiProject(String response) {
        if (response == null) return null;
        String jsonText = response.trim();
        if (jsonText.startsWith("```")) {
            int firstLine = jsonText.indexOf('\n');
            int lastFence = jsonText.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) jsonText = jsonText.substring(firstLine + 1, lastFence).trim();
        }
        int start = jsonText.indexOf('{');
        int end = jsonText.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            JSONObject json = new JSONObject(jsonText.substring(start, end + 1));
            WebsiteProject project = createProject(json.optString("name", "BEASTY Website"),
                    json.optString("description", ""));
            if (project == null) return null;
            project.updateCode(json.optString("html", ""), json.optString("css", ""),
                    json.optString("javascript", ""));
            return isReadyToPreview(project) ? project : null;
        } catch (Exception e) { return null; }
    }

    public boolean isReadyToPreview(WebsiteProject project) {
        return project != null && project.html != null && !project.html.trim().isEmpty();
    }

    public String prepareWebsiteCode(WebsiteProject project) {
        if (!isReadyToPreview(project)) return "";
        String html = project.html;
        String css = project.css == null ? "" : project.css;
        String js = project.javascript == null ? "" : project.javascript;
        int headEnd = html.toLowerCase(java.util.Locale.ROOT).indexOf("</head>");
        if (headEnd >= 0) {
            html = html.substring(0, headEnd) + "<style>" + css + "</style>" + html.substring(headEnd);
        } else {
            html = "<style>" + css + "</style>" + html;
        }
        int bodyEnd = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</body>");
        if (bodyEnd >= 0) {
            html = html.substring(0, bodyEnd) + "<script>" + js + "</script>" + html.substring(bodyEnd);
        } else {
            html += "<script>" + js + "</script>";
        }
        return html;
    }

    public boolean approveForDeployment(WebsiteProject project) {
        if (!isReadyToPreview(project)) return false;
        project.status = "Approved for deployment";
        return saveProject(project);
    }

    private WebsiteProject fromJson(JSONObject json) {
        WebsiteProject project = new WebsiteProject(json.optString("name"), json.optString("description"));
        project.updateCode(json.optString("html"), json.optString("css"), json.optString("javascript"));
        project.status = json.optString("status", "Ready for preview");
        return project;
    }
}
