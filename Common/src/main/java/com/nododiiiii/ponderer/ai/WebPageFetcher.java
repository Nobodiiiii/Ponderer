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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        try {
            if (com.nododiiiii.ponderer.Config.AI_WEB_USE_PROXY.get()) {
                return HttpClientFactory.get();
            }
        } catch (Throwable ignored) {
            // Common's isolated unit-test runtime does not include ForgeConfigSpec.
        }
        return PLAIN_CLIENT;
    }

    /**
     * Fetch a URL and extract text + images.
     */
    public static WebPageContent fetch(String url) throws IOException, InterruptedException {
        return fetch(url, true);
    }

    /**
     * Fetch a URL and extract text, optionally downloading images.
     */
    public static WebPageContent fetch(String url, boolean includeImages) throws IOException, InterruptedException {
        return fetchInternal(url, includeImages, null);
    }

    public static WebPageContent fetch(String url, boolean includeImages,
                                       AiCancellationScope cancellation) throws IOException, InterruptedException {
        return fetchInternal(url, includeImages, cancellation);
    }

    private static WebPageContent fetchInternal(String url, boolean includeImages,
                                                AiCancellationScope cancellation)
        throws IOException, InterruptedException {
        if (cancellation != null) {
            cancellation.throwIfCancelled();
        }
        HttpClient client = getClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();

        HttpResponse<String> response;
        if (cancellation == null) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } else {
            CompletableFuture<HttpResponse<String>> requestFuture =
                cancellation.track(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            try {
                response = requestFuture.join();
            } catch (CancellationException e) {
                throw e;
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                if (cause instanceof InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
                throw e;
            }
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }

        String html = response.body();
        String text = extractText(html);
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "\n... (truncated)";
        }

        List<ImageData> images = includeImages
            ? extractAndDownloadImages(html, url, client, cancellation)
            : List.of();
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
    private static List<ImageData> extractAndDownloadImages(String html, String pageUrl, HttpClient client,
                                                            AiCancellationScope cancellation) {
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
            try {
                URI resolved = URI.create(pageUrl).resolve(src);
                String scheme = resolved.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    continue;
                }
                src = resolved.toString();
            } catch (Exception e) {
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
            CompletableFuture<HttpResponse<byte[]>> responseFuture =
                client.sendAsync(imgReq, HttpResponse.BodyHandlers.ofByteArray());
            if (cancellation != null) {
                cancellation.track(responseFuture);
            }
            CompletableFuture<ImageData> imageFuture =
                responseFuture
                    .thenApply(imgResp -> {
                        if (imgResp.statusCode() != 200) return null;
                        byte[] data = imgResp.body();
                        if (data.length < 5000 || data.length > 5_000_000) return null;
                        String contentType = imgResp.headers().firstValue("content-type").orElse("image/png");
                        if (!contentType.startsWith("image/")) return null;
                        if (contentType.contains(";")) contentType = contentType.substring(0, contentType.indexOf(';')).trim();
                        return new ImageData(Base64.getEncoder().encodeToString(data), contentType);
                    })
                    .exceptionally(ex -> null);
            futures.add(cancellation == null ? imageFuture : cancellation.track(imageFuture));
        }

        // Collect results, keeping order, up to MAX_IMAGES
        List<ImageData> images = new ArrayList<>();
        for (CompletableFuture<ImageData> f : futures) {
            if (images.size() >= MAX_IMAGES) break;
            try {
                if (cancellation != null) {
                    cancellation.throwIfCancelled();
                }
                ImageData img = f.join();
                if (img != null) images.add(img);
            } catch (Exception ignored) {}
        }
        if (cancellation != null) {
            cancellation.throwIfCancelled();
        }
        return images;
    }
}
