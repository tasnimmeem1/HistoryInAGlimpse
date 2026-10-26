package com.historyinaglimpse.animation_backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FarmingSceneServiceTests {
    private HttpServer server;
    private FarmingSceneService service;
    private String rows;
    private int storageStatus;
    private final AtomicInteger downloads = new AtomicInteger();
    private static final byte[] IMAGE = { (byte)137, 80, 78, 71 };

    @BeforeEach void start() throws Exception {
        rows = "[{\"id\":\"early-farming\",\"bucket_name\":\"animation-assets\","
                + "\"background_path\":\"early-farming/farming-background.png\","
                + "\"characters_path\":\"early-farming/farming-actors.png\",\"settings\":{\"canvasWidth\":1000}}]";
        storageStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/animation_scenes", e -> {
            if (!"test-key".equals(e.getRequestHeaders().getFirst("apikey"))) {
                e.sendResponseHeaders(401, -1); e.close(); return;
            }
            byte[] body = rows.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(200, body.length);
            try (var out = e.getResponseBody()) { out.write(body); }
        });
        server.createContext("/storage/v1/object/authenticated/animation-assets/early-farming/", e -> {
            downloads.incrementAndGet();
            if (!"test-key".equals(e.getRequestHeaders().getFirst("apikey"))) {
                e.sendResponseHeaders(401, -1); e.close(); return;
            }
            e.getResponseHeaders().add("Content-Type", "image/png");
            e.sendResponseHeaders(storageStatus, IMAGE.length);
            try (var out = e.getResponseBody()) { out.write(IMAGE); }
        });
        server.start();
        service = new FarmingSceneService("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void readsSceneAndDownloadsBothPrivateImages() {
        assertEquals("early-farming", service.getScene().get("id"));
        assertArrayEquals(IMAGE, service.getImage("background"));
        assertArrayEquals(IMAGE, service.getImage("characters"));
        assertEquals(2, downloads.get());
    }
    @Test void missingSceneReturnsNotFound() {
        rows = "[]";
        assertEquals(404, assertThrows(ResponseStatusException.class, service::getScene).getStatusCode().value());
    }
    @Test void rejectsInvalidBucketBeforeDownloading() {
        rows = rows.replace("animation-assets", "another-bucket");
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.getImage("background")).getStatusCode().value());
        assertEquals(0, downloads.get());
    }
    @Test void storageFailureReturnsBadGateway() {
        storageStatus = 403;
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.getImage("characters")).getStatusCode().value());
    }
}
