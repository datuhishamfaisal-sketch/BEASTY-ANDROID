package com.beasty.assistant;

import android.content.Context;
import java.util.Locale;

/**
 * Routes natural-language requests to the right Business Hub workflow.
 * Live research is enabled through Gemini Google Search grounding when available.
 */
public final class BusinessHubRouter {
    private BusinessHubRouter() {}

    public static String buildInstruction(Context context, String request) {
        String q = normalize(request);
        String route = detectRoute(q);
        if (route.isEmpty()) return "";

        return "\nBUSINESS_HUB_LIVE_RESEARCH: " +
                "You are handling a Business Hub request. Infer the user's goal from ordinary Malay or English. " +
                "The user never needs to name a module, choose a menu, or press a module button. " +
                "Select and combine workflows automatically: Business Hunter for business opportunities; " +
                "Trend Hunter for trending products and demand; Supplier Hunter for manufacturers, wholesalers and sourcing; " +
                "Tender Hunter for tenders and quotations; Market Research for competitors, pricing, demand and margins; " +
                "Auto Business Planner for startup costs and business plans; Lead Manager for prospect tracking; " +
                "AI Negotiator for drafting offers and negotiation messages. " +
                "Use current web research when the request needs current suppliers, tender notices, product trends, prices, " +
                "company details or market facts. Give source names and working source links where available. " +
                "Never invent search results, company names, phone numbers, prices, dates, tender status or supplier availability. " +
                "Separate verified facts from estimates and state when reliable current sources cannot be found. " +
                "For Sabah construction tenders, prioritize Sabah and consider CIDB G1-G2, PUKONSA G1, SPKK and STB only when relevant; " +
                "do not assume the user's licence status. Ask only one concise follow-up if an essential detail is missing. " +
                "Do not claim to contact anyone, save a lead, submit a tender, buy anything or complete an external action unless the app did it. " +
                "Sending messages, payments, purchases, submissions, publishing and other external or irreversible actions require explicit approval. " +
                "Detected workflow(s): " + route + ". Respond in the user's language with practical findings and next steps.";
    }

    public static boolean isResearchRequest(String request) {
        return !detectRoute(normalize(request)).isEmpty();
    }

    private static String detectRoute(String q) {
        StringBuilder route = new StringBuilder();
        if (has(q, "tender", "sebutharga", "sebut harga", "quotation", "cidb", "pukonsa", "spkk", "stb", "kontrak kerajaan"))
            route.append("Tender Hunter; ");
        if (has(q, "supplier", "pembekal", "kilang", "factory", "wholesale", "borong", "import", "eksport", "sourcing"))
            route.append("Supplier Hunter; ");
        if (has(q, "trending", "trend", "viral", "laku", "laris", "produk panas", "popular", "permintaan pasaran"))
            route.append("Trend Hunter; ");
        if (has(q, "pesaing", "competitor", "harga pasaran", "market research", "kajian pasaran", "margin", "untung", "permintaan"))
            route.append("Market Research; ");
        if (has(q, "modal", "rancang bisnes", "rancangan bisnes", "business plan", "kos operasi", "mula bisnes", "start business"))
            route.append("Auto Business Planner; ");
        if (has(q, "prospek", "lead", "pelanggan", "customer", "crm", "senarai client", "senarai pelanggan"))
            route.append("Lead Manager; ");
        if (has(q, "runding", "nego", "negotiat", "diskaun", "tawar harga", "counter offer"))
            route.append("AI Negotiator; ");
        if (has(q, "peluang bisnes", "peluang perniagaan", "idea bisnes", "idea perniagaan", "cari bisnes", "bisnes apa", "business opportunity"))
            route.append("Business Hunter; ");
        if (route.length() == 0 && has(q, "bisnes", "business", "jual", "jualan", "produk", "pasaran", "cari"))
            route.append("Business Hub intent detection; ");
        return route.toString().trim();
    }

    private static boolean has(String q, String... words) {
        for (String word : words) if (q.contains(word)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
