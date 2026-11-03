package com.beasty.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds reusable image candidates from Wikimedia Commons and can guarantee a visible gallery in website HTML. */
public class ImageAssetHunter {
    public String searchForWebsite(String userRequest) {
        LinkedHashMap<String, String> queries = buildQueries(userRequest);
        LinkedHashMap<String, JSONObject> found = new LinkedHashMap<>();
        for (String query : queries.keySet()) {
            try {
                String endpoint = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                        + "&gsrsearch=" + URLEncoder.encode(query, "UTF-8")
                        + "&gsrnamespace=6&gsrlimit=50&prop=imageinfo"
                        + "&iiprop=url%7Cextmetadata&iiurlwidth=1400&format=json";
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "BEASTY-Android/1.0 (website image research; contact: support@beasty.app)");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    connection.disconnect();
                    continue;
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
                JSONObject queryObject = root.optJSONObject("query");
                JSONObject pages = queryObject == null ? null : queryObject.optJSONObject("pages");
                if (pages == null) continue;
                java.util.Iterator<String> keys = pages.keys();
                while (keys.hasNext()) {
                    JSONObject page = pages.optJSONObject(keys.next());
                    if (page == null) continue;
                    JSONArray infos = page.optJSONArray("imageinfo");
                    if (infos == null || infos.length() == 0) continue;
                    JSONObject info = infos.optJSONObject(0);
                    if (info == null) continue;
                    String imageUrl = info.optString("thumburl", info.optString("url", ""));
                    String sourceUrl = info.optString("descriptionurl", "");
                    if (!imageUrl.startsWith("https://") || !sourceUrl.startsWith("https://")) continue;
                    JSONObject metadata = info.optJSONObject("extmetadata");
                    JSONObject asset = new JSONObject();
                    asset.put("title", page.optString("title", "Image"));
                    asset.put("imageUrl", imageUrl);
                    asset.put("sourceUrl", sourceUrl);
                    asset.put("license", metadata == null ? "" : stripHtml(metadata.optJSONObject("LicenseShortName")));
                    asset.put("credit", metadata == null ? "" : stripHtml(metadata.optJSONObject("Artist")));
                    found.put(imageUrl, asset);
                }
            } catch (Exception ignored) {
                // Try the next focused search phrase if one request fails.
            }
            // Continue through all matching results.
        }
        StringBuilder assets = new StringBuilder();
        for (JSONObject asset : found.values()) {
            assets.append("\n- Title: ").append(asset.optString("title"))
                    .append("\n  Image URL: ").append(asset.optString("imageUrl"))
                    .append("\n  Source/license page: ").append(asset.optString("sourceUrl"))
                    .append("\n  License: ").append(asset.optString("license", "Check source page"))
                    .append("\n  Credit: ").append(asset.optString("credit", "See source page")).append("\n");
        }
        return assets.toString();
    }

    private LinkedHashMap<String, String> buildQueries(String request) {
        String text = request == null ? "" : request.toLowerCase(java.util.Locale.ROOT);
        LinkedHashMap<String, String> queries = new LinkedHashMap<>();
        if (containsAny(text, "jalan", "road", "highway", "asphalt", "lebuh raya")) {
            queries.put("road construction asphalt road", "");
            queries.put("highway construction roadworks", "");
        }
        if (containsAny(text, "bangunan", "building", "architecture", "rumah", "office", "construction", "kontraktor", "contractor")) {
            queries.put("building construction site architecture", "");
            queries.put("construction workers building site", "");
        }
        if (containsAny(text, "landskap", "landscape", "taman", "garden", "pokok", "plant")) {
            queries.put("landscape architecture garden", "");
        }
        if (containsAny(text, "jambatan", "bridge")) queries.put("bridge construction engineering", "");
        if (queries.isEmpty()) {
            queries.put("building architecture modern", "");
            queries.put("road construction", "");
            queries.put("business office workspace", "");
        }
        return queries;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    /** Injects a visible, credited gallery if the AI omitted all retrieved images. */
    public String ensureImagesInHtml(String html, String assets) {
        if (html == null || assets == null || assets.trim().isEmpty()) return html;
        List<String[]> images = new ArrayList<>();
        String[] blocks = assets.split("\\n- Title: ");
        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            String title = lineValue(block, null);
            String image = lineValue(block, "Image URL: ");
            String source = lineValue(block, "Source/license page: ");
            String license = lineValue(block, "License: ");
            String credit = lineValue(block, "Credit: ");
            if (image.startsWith("https://") && source.startsWith("https://")) {
                images.add(new String[]{title, image, source, license, credit});
            }
        }
        if (images.isEmpty()) return html;
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        boolean anyAlreadyUsed = false;
        for (String[] item : images) if (html.contains(item[1])) anyAlreadyUsed = true;
        if (anyAlreadyUsed) return html;

        StringBuilder gallery = new StringBuilder();
        gallery.append("<section style='padding:48px 6%;background:#f3f6fa;color:#172033'>")
                .append("<h2 style='font-size:clamp(24px,4vw,38px);margin:0 0 22px'>Galeri Projek & Inspirasi</h2>")
                .append("<div style='display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:18px'>");
        for (String[] item : images) {
            gallery.append("<figure style='margin:0;background:white;border-radius:14px;overflow:hidden;box-shadow:0 5px 20px #0001'>")
                    .append("<img loading='lazy' style='display:block;width:100%;height:210px;object-fit:cover' src='")
                    .append(item[1]).append("' alt='").append(escapeHtml(item[0])).append("'>")
                    .append("<figcaption style='padding:12px;font-size:12px;line-height:1.5'>")
                    .append("<strong>").append(escapeHtml(item[0])).append("</strong><br>")
                    .append("Credit: ").append(escapeHtml(item[4].isEmpty() ? "See source page" : item[4])).append("<br>")
                    .append("License: ").append(escapeHtml(item[3].isEmpty() ? "Check source page" : item[3])).append("<br>")
                    .append("<a href='").append(item[2]).append("' target='_blank' rel='noopener'>Image source & license</a>")
                    .append("</figcaption></figure>");
        }
        gallery.append("</div></section>");
        int bodyEnd = lower.lastIndexOf("</body>");
        return bodyEnd >= 0 ? html.substring(0, bodyEnd) + gallery + html.substring(bodyEnd) : html + gallery;
    }

    private String lineValue(String block, String prefix) {
        if (prefix == null) {
            int end = block.indexOf('\n');
            return (end < 0 ? block : block.substring(0, end)).trim();
        }
        for (String line : block.split("\\n")) if (line.trim().startsWith(prefix)) return line.trim().substring(prefix.length()).trim();
        return "";
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String stripHtml(JSONObject value) {
        if (value == null) return "";
        String text = value.optString("value", "");
        return text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }
}
