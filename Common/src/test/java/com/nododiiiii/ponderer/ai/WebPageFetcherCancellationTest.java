package com.nododiiiii.ponderer.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WebPageFetcherCancellationTest {

    @Test
    @Timeout(10)
    void cancellationStopsAnInFlightImageDownload() throws Exception {
        CountDownLatch imageStarted = new CountDownLatch(1);
        CountDownLatch releaseImage = new CountDownLatch(1);
        ExecutorService serverExecutor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            byte[] body = ("<html><body>Reference text<img src=\"http://127.0.0.1:"
                + server.getAddress().getPort() + "/asset.png\"></body></html>")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/asset.png", exchange -> {
            imageStarted.countDown();
            try {
                releaseImage.await(5, TimeUnit.SECONDS);
                byte[] body = new byte[6000];
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            AiCancellationScope cancellation = new AiCancellationScope();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            CompletableFuture<WebPageFetcher.WebPageContent> fetch =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return WebPageFetcher.fetch(url, true, cancellation);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });

            if (!imageStarted.await(5, TimeUnit.SECONDS)) {
                if (fetch.isDone()) {
                    String outcome = fetch.handle((value, error) -> error == null
                        ? "completed with " + value.images().size() + " image(s)"
                        : "failed: " + error).join();
                    fail("Image request did not start; fetch " + outcome);
                }
                fail("Image request did not start; fetch is still pending");
            }
            assertTrue(cancellation.cancel());
            releaseImage.countDown();
            assertThrows(CompletionException.class, fetch::join);
        } finally {
            releaseImage.countDown();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }
}
