package com.beasty.assistant;

import org.json.JSONObject;

public class WebCreator {

    public static class WebsiteProject {
        public String name;
        public String description;
        public String html;
        public String css;
        public String javascript;
        public String status;

        public WebsiteProject(String name, String description) {
            this.name = name;
            this.description = description;
            this.html = "";
            this.css = "";
            this.javascript = "";
            this.status = "Draft";
        }

        public void updateCode(
            String html,
            String css,
            String javascript
        ) {
            this.html = html == null ? "" : html;
            this.css = css == null ? "" : css;
            this.javascript = javascript == null ? "" : javascript;
            this.status = "Ready for preview";
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            try {
                json.put("name", name);
                json.put("description", description);
                json.put("html", html);
                json.put("css", css);
                json.put("javascript", javascript);
                json.put("status", status);
            } catch (Exception ignored) {}

            return json;
        }
    }

    public WebsiteProject createProject(
        String name,
        String description
    ) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        return new WebsiteProject(
            name.trim(),
            description == null ? "" : description.trim()
        );
    }

    public boolean isReadyToPreview(WebsiteProject project) {
        return project != null
            && project.html != null
            && !project.html.trim().isEmpty();
    }

    public String prepareWebsiteCode(WebsiteProject project) {
        if (!isReadyToPreview(project)) {
            return "";
        }

        return project.html
            + "\n<style>\n" + project.css + "\n</style>"
            + "\n<script>\n" + project.javascript + "\n</script>";
    }

    public boolean approveForDeployment(WebsiteProject project) {
        if (!isReadyToPreview(project)) {
            return false;
        }

        project.status = "Approved for deployment";
        return true;
    }
}
