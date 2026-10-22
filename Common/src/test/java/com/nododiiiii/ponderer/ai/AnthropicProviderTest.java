package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnthropicProviderTest {

    @Test
    @Timeout(10)
    void translatesClaudeCodeOneMillionModelSuffixToBetaHeader() throws Exception {
        AtomicReference<String> betaHeader = new AtomicReference<>();
        AtomicReference<JsonObject> requestBody = new AtomicReference<>();
        ExecutorService serverExecutor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/v1/messages", exchange -> {
            betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            requestBody.set(JsonParser.parseString(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] body = "{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            String result = new AnthropicProvider(64)
                .generate(
                    "Reply briefly.",
                    List.of(new LlmProvider.ContentBlock.Text("Test")),
                    baseUrl,
                    "test-key",
                    "claude-opus-4-8[1m]",
                    new AiCancellationScope())
                .get(5, TimeUnit.SECONDS);

            assertEquals("OK", result);
            assertEquals("claude-opus-4-8", requestBody.get().get("model").getAsString());
            assertEquals("context-1m-2025-08-07", betaHeader.get());
        } finally {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }
}
