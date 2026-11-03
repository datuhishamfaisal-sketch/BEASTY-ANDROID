package com.beasty.assistant;

import org.json.JSONObject;

public class AppCreator {

    public static class AppProject {
        public String name;
        public String description;
        public String platform;
        public String packageName;
        public String generatedCode;
        public String status;

        public AppProject(String name, String description) {
            this.name = name;
            this.description = description;
            this.platform = "Android";
            this.packageName = "";
            this.generatedCode = "";
            this.status = "Draft";
        }

        public void updateCode(String code) {
            generatedCode = code == null ? "" : code;
            status = generatedCode.trim().isEmpty()
                ? "Draft" : "Code generated";
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            try {
                json.put("name", name);
                json.put("description", description);
                json.put("platform", platform);
                json.put("packageName", packageName);
                json.put("generatedCode", generatedCode);
                json.put("status", status);
            } catch (Exception ignored) {}

            return json;
        }
    }

    public AppProject createProject(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        return new AppProject(
            name.trim(),
            description == null ? "" : description.trim()
        );
    }

    public boolean setPackageName(AppProject project, String packageName) {
        if (project == null || packageName == null) {
            return false;
        }

        String value = packageName.trim();

        if (!value.matches(
            "[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"
        )) {
            return false;
        }

        project.packageName = value;
        return true;
    }

    public boolean isReady(AppProject project) {
        return project != null
            && project.generatedCode != null
            && !project.generatedCode.trim().isEmpty();
    }

    public String getGeneratedCode(AppProject project) {
        return isReady(project) ? project.generatedCode : "";
    }

    public boolean approveProject(AppProject project) {
        if (!isReady(project)) {
            return false;
        }

        project.status = "Approved for development";
        return true;
    }
}
