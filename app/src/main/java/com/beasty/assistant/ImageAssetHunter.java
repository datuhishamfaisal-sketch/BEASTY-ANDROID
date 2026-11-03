package com.beasty.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Finds reusable image candidates from Wikimedia Commons and returns source/license details for the AI. */
public class ImageAssetHunter {
    public String searchForWebsite(String userRequest) {
        try {
            String query = userRequest == null ? "" : userRequest.trim();
            if (query.length() > 180) query = query.substring(0, 180);
            String endpoint = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                    + "&gsrsearch=" + URLEncoder.encode(query + " filetype:bitmap", "UTF-8")
                    + "&gsrnamespace=6&gsrlimit=8&prop=imageinfo"
                    + "&iiprop=url%7Cextmetadata&iiurlwidth=1400&format=json";
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "BEASTY-Android/1.0 (website image research)");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                connection.disconnect();
                return "";
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            } finally {
                connection.disconnect();
            }
            JSONObject root = new JSONObject(response.toString());
            JSONObject pages = root.optJSONObject("query") == null ? null
                    : root.getJSONObject("query").optJSONObject("pages");
            if (pages == null) return "";
            StringBuilder assets = new StringBuilder();
            int added = 0;
            java.util.Iterator<String> keys = pages.keys();
            while (keys.hasNext() && added < 8) {
                JSONObject page = pages.optJSONObject(keys.next());
                if (page == null) continue;
                JSONArray infos = page.optJSONArray("imageinfo");
                if (infos == null || infos.length() == 0) continue;
                JSONObject info = infos.optJSONObject(0);
                if (info == null) continue;
                String imageUrl = info.optString("thumburl", info.optString("url", ""));
                String sourceUrl = info.optString("descriptionurl", "");
                JSONObject metadata = info.optJSONObject("extmetadata");
                String license = metadata == null ? "" : stripHtml(metadata.optJSONObject("LicenseShortName"));
                String artist = metadata == null ? "" : stripHtml(metadata.optJSONObject("Artist"));
                String title = page.optString("title", "Image");
                if (!imageUrl.startsWith("https://") || sourceUrl.isEmpty()) continue;
                assets.append("\n- Title: ").append(title)
                        .append("\n  Image URL: ").append(imageUrl)
                        .append("\n  Source/license page: ").append(sourceUrl)
                        .append("\n  License: ").append(license.isEmpty() ? "Check source page" : license)
                        .append("\n  Credit: ").append(artist.isEmpty() ? "See source page" : artist).append("\n");
                added++;
            }
            return assets.length() == 0 ? "" : assets.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String stripHtml(JSONObject value) {
        if (value == null) return "";
        String text = value.optString("value", "");
        return text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }
}
