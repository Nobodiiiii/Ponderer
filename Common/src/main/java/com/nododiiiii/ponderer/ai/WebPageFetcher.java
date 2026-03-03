package com.nododiiiii.ponderer.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a web page and extracts text content and images for LLM consumption.
 */
public class WebPageFetcher {

    private static final int MAX_IMAGES = 5;
    private static final int MAX_TEXT_LENGTH = 30000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** Plain HttpClient without proxy, used when web proxy is disabled. */
    private static final HttpClient PLAIN_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Result of fetching a web page. */
    public record WebPageContent(String text, List<ImageData> images) {}
    public record ImageData(String base64, String mediaType) {}

    private static HttpClient getClient() {
        return com.nododiiiii.ponderer.Config.AI_WEB_USE_PROXY.get()
            ? HttpClientFactory.get() : PLAIN_CLIENT;
    }

    /**
     * Fetch a URL and extract text + images.
     */
    public static WebPageContent fetch(String url) throws IOException, InterruptedException {
        HttpClient client = getClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }

        String html = response.body();
        String text = extractText(html);
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "\n... (truncated)";
        }

        List<ImageData> images = extractAndDownloadImages(html, url, client);
        return new WebPageContent(text, images);
    }

    /**
     * Simple HTML to text conversion: strip tags, decode entities, normalize whitespace.
     */
    private static String extractText(String html) {
        // Remove script and style blocks
        String cleaned = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        cleaned = cleaned.replaceAll("(?is)<nav[^>]*>.*?</nav>", "");
        cleaned = cleaned.replaceAll("(?is)<header[^>]*>.*?</header>", "");
        cleaned = cleaned.replaceAll("(?is)<footer[^>]*>.*?</footer>", "");
        // Replace block-level tags with newlines
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?i)</?(p|div|h[1-6]|li|tr|td|th|blockquote|pre)[^>]*>", "\n");
        // Strip remaining tags
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        // Decode common HTML entities
        cleaned = cleaned.replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&nbsp;", " ").replace("&#39;", "'");
        // Normalize whitespace
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n\\s*\\n+", "\n\n");
        return cleaned.trim();
    }

    /**
     * Extract image URLs from HTML, filter by likely content images, download in parallel and encode.
     */
    private static List<ImageData> extractAndDownloadImages(String html, String pageUrl, HttpClient client) {
        Pattern imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = imgPattern.matcher(html);

        List<String> candidateUrls = new ArrayList<>();
        while (matcher.find() && candidateUrls.size() < 20) {
            String src = matcher.group(1);
            // Skip tiny icons, data URIs, and tracking pixels
            if (src.startsWith("data:")) continue;
            if (src.contains("icon") || src.contains("logo") || src.contains("avatar")) continue;
            if (src.contains("1x1") || src.contains("pixel")) continue;
            // Resolve relative URLs
            if (src.startsWith("//")) {
                src = "https:" + src;
            } else if (src.startsWith("/")) {
                try {
                    URI base = URI.create(pageUrl);
                    src = base.getScheme() + "://" + base.getHost() + src;
                } catch (Exception e) {
                    continue;
                }
            } else if (!src.startsWith("http")) {
                continue;
            }
            candidateUrls.add(src);
        }

        // Download images in parallel
        List<CompletableFuture<ImageData>> futures = new ArrayList<>();
        for (String imgUrl : candidateUrls) {
            if (futures.size() >= MAX_IMAGES * 2) break; // fetch extra candidates in case some fail
            HttpRequest imgReq = HttpRequest.newBuilder()
                .uri(URI.create(imgUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
            futures.add(
                client.sendAsync(imgReq, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(imgResp -> {
                        if (imgResp.statusCode() != 200) return null;
                        byte[] data = imgResp.body();
                        if (data.length < 5000 || data.length > 5_000_000) return null;
                        String contentType = imgResp.headers().firstValue("content-type").orElse("image/png");
                        if (!contentType.startsWith("image/")) return null;
                        if (contentType.contains(";")) contentType = contentType.substring(0, contentType.indexOf(';')).trim();
                        return new ImageData(Base64.getEncoder().encodeToString(data), contentType);
                    })
                    .exceptionally(ex -> null)
            );
        }

        // Collect results, keeping order, up to MAX_IMAGES
        List<ImageData> images = new ArrayList<>();
        for (CompletableFuture<ImageData> f : futures) {
            if (images.size() >= MAX_IMAGES) break;
            try {
                ImageData img = f.join();
                if (img != null) images.add(img);
            } catch (Exception ignored) {}
        }
        return images;
    }
}
