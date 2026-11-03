package com.beasty.assistant;

import org.json.JSONObject;

public class VideoCreator {

    public static class VideoProject {
        public String title;
        public String prompt;
        public String style;
        public String duration;
        public String aspectRatio;
        public String status;
        public String videoUrl;

        public VideoProject(String title, String prompt) {
            this.title = title;
            this.prompt = prompt;
            this.style = "Cinematic";
            this.duration = "30 seconds";
            this.aspectRatio = "16:9";
            this.status = "Draft";
            this.videoUrl = "";
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            try {
                json.put("title", title);
                json.put("prompt", prompt);
                json.put("style", style);
                json.put("duration", duration);
                json.put("aspectRatio", aspectRatio);
                json.put("status", status);
                json.put("videoUrl", videoUrl);
            } catch (Exception ignored) {}

            return json;
        }
    }

    public VideoProject createProject(String title, String prompt) {
        if (title == null || title.trim().isEmpty()
            || prompt == null || prompt.trim().isEmpty()) {
            return null;
        }

        return new VideoProject(title.trim(), prompt.trim());
    }

    public boolean configure(
        VideoProject project,
        String style,
        String duration,
        String aspectRatio
    ) {
        if (project == null) return false;

        if (style != null && !style.trim().isEmpty()) {
            project.style = style.trim();
        }

        if (duration != null && !duration.trim().isEmpty()) {
            project.duration = duration.trim();
        }

        if ("16:9".equals(aspectRatio)
            || "9:16".equals(aspectRatio)
            || "1:1".equals(aspectRatio)) {
            project.aspectRatio = aspectRatio;
        }

        return true;
    }

    public String buildGenerationPrompt(VideoProject project) {
        if (project == null) return "";

        return "Create a video titled: " + project.title
            + ". Concept: " + project.prompt
            + ". Visual style: " + project.style
            + ". Duration: " + project.duration
            + ". Aspect ratio: " + project.aspectRatio;
    }

    public boolean setGeneratedVideo(
        VideoProject project,
        String videoUrl
    ) {
        if (project == null || videoUrl == null
            || !videoUrl.startsWith("https://")) {
            return false;
        }

        project.videoUrl = videoUrl;
        project.status = "Video generated";
        return true;
    }
}
