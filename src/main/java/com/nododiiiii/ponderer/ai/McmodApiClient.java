package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Client for interacting with MCMod API to get item information
 */
public final class McmodApiClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private McmodApiClient() {
    }

    /**
     * Get MCMod item URL for the given item display name
     * 
     * @param displayName Item display name
     * @return Optional URL if found
     */
    public static Optional<String> getItemUrl(String displayName) {
        try {
            // Call MCMod API to get item ID
            var client = HttpClientFactory.get();
            String encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mcmod.cn/getItem/?regname=" + encodedName))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Parse response to get item ID
            int itemId = -1;
            try {
                // Simple parsing - assuming response is just the ID
                itemId = Integer.parseInt(responseBody.trim());
            } catch (NumberFormatException e) {
                // Try to parse as JSON if available
                try {
                    JsonElement element = JsonParser.parseString(responseBody);
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                        itemId = element.getAsInt();
                    }
                } catch (Exception ex) {
                    // Ignore JSON parsing errors
                }
            }

            if (itemId > 0) {
                // Generate MCMod URL
                String mcmodUrl = "https://www.mcmod.cn/item/" + itemId + ".html";
                return Optional.of(mcmodUrl);
            }
        } catch (Exception e) {
            // Log error and return empty
            LOGGER.warn("Failed to get MCMod URL for item '{}': {}", displayName, e.getMessage());
        }
        return Optional.empty();
    }
}