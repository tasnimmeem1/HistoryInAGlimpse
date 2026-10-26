package com.historyinaglimpse.animation_backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EraSceneServiceTests {
    private HttpServer server;
    private EraSceneService service;
    private String replacement;
    private int storageStatus = 200;
    private int mp3Missing;
    private String lastPath;
    private final AtomicInteger downloads = new AtomicInteger();
    private static final byte[] BYTES = {1, 2, 3, 4};
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/animation_scenes", e -> {
            String id = e.getRequestURI().getQuery().split("&")[0].substring(6);
            String json = "[{\"id\":\"" + id + "\",\"bucket_name\":\"animation-assets\","
                + "\"background_path\":\"" + id + "/background.png\",\"characters_path\":\"" + id + "/actors.png\","
                + "\"settings\":{\"map_path\":\"" + id + "/map.png\",\"audio_path\":\"" + id + "/ambience.wav\"}}]";
            byte[] body = (replacement == null ? json : replacement).getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(200, body.length);
            try (var out = e.getResponseBody()) { out.write(body); }
        });
        server.createContext("/storage/v1/object/authenticated/animation-assets/", e -> {
            downloads.incrementAndGet();
            lastPath = e.getRequestURI().getPath();
            if (lastPath.endsWith(".mp3") && mp3Missing != 0) {
                byte[] missing = "{\"message\":\"Object not found\"}".getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().add("Content-Type", "application/json");
                e.sendResponseHeaders(mp3Missing, missing.length);
                try (var out = e.getResponseBody()) { out.write(missing); }
                return;
            }
            assertEquals("test-key", e.getRequestHeaders().getFirst("apikey"));
            e.getResponseHeaders().add("Content-Type", "application/octet-stream");
            e.sendResponseHeaders(storageStatus, BYTES.length);
            try (var out = e.getResponseBody()) { out.write(BYTES); }
        });
        server.start();
        service = new EraSceneService("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void allFiveErasLoadMetadataAndFourPrivateAssets() {
        for (String id : List.of("sumerian-urban", "egypt-old-kingdom", "indus-valley", "akkadian-empire", "egypt-middle-kingdom")) {
            assertEquals(id, service.getScene(id).get("id"));
            for (String kind : List.of("background", "characters", "map", "audio")) assertArrayEquals(BYTES, service.getAsset(id, kind));
        }
        assertEquals(20, downloads.get());
    }
    @Test void bothNewBatchesUseTheirOwnPrivateMediaAndMp3() {
        for (String id : List.of("hittite-kingdom", "shang-dynasty", "egypt-new-kingdom", "phoenician-network", "zhou-dynasty", "neo-assyrian-empire", "archaic-greece", "neo-babylonian-period", "achaemenid-persia", "classical-greece", "mauryan-empire", "parthian-empire", "qin-dynasty", "han-dynasty", "western-imperial-rome", "aksumite-empire", "sasanian-empire", "classic-maya", "eastern-roman-empire", "ghana-wagadu", "gupta-empire")) {
            replacement = "[{\"id\":\""+id+"\",\"bucket_name\":\"animation-assets\",\"background_path\":\""+id+"/background.png\",\"characters_path\":\""+id+"/actors.png\",\"settings\":{\"map_path\":\""+id+"/map.png\",\"audio_path\":\""+id+"/ambiance.mp3\"}}]";
            assertEquals(id, service.getScene(id).get("id"));
            for (String kind : List.of("background", "characters", "map")) {
                assertArrayEquals(BYTES, service.getAsset(id, kind));
                assertTrue(lastPath.contains("/"+id+"/"));
            }
            assertEquals("audio/mpeg", service.getAudio(id).contentType());
            assertTrue(lastPath.endsWith(id+"/ambiance.mp3"));
        }
        assertEquals(84, downloads.get());
    }
    @Test void missingNewSoundNeverUsesAnotherEra() {
        replacement = "[{\"bucket_name\":\"animation-assets\",\"settings\":{\"audio_path\":\"hittite-kingdom/ambiance.mp3\"}}]";
        mp3Missing=404;
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAudio("hittite-kingdom")).getStatusCode().value());
        assertEquals(1, downloads.get());
        assertTrue(lastPath.endsWith("hittite-kingdom/ambiance.mp3"));
    }
    @Test void royalSocietyHasPrivateSceneAndOwnAudio() {
        replacement = "[{\"id\":\"royal-society\",\"bucket_name\":\"animation-assets\",\"background_path\":\"royal-society/background.png\",\"characters_path\":\"royal-society/actors.png\",\"settings\":{\"map_path\":\"royal-society/map.png\",\"audio_path\":\"royal-society/ambiance.mp3\"}}]";
        assertEquals("royal-society", service.getScene("royal-society").get("id"));
        for (String kind : List.of("background", "characters", "map")) assertArrayEquals(BYTES, service.getAsset("royal-society",kind));
        assertEquals("audio/mpeg",service.getAudio("royal-society").contentType());
        assertTrue(lastPath.endsWith("royal-society/ambiance.mp3"));
    }
    @Test void newtonsSharedAudioDoesNotRequireSceneImages() {
        replacement = "[{\"id\":\"newtons-laws\",\"bucket_name\":\"animation-assets\",\"background_path\":\"\",\"characters_path\":\"\",\"settings\":{\"view\":\"newton-laws\",\"audio_path\":\"newtons-laws/ambiance.mp3\"}}]";
        assertEquals("audio/mpeg",service.getAudio("newtons-laws").contentType());
        assertArrayEquals(BYTES,service.getAudio("newtons-laws").bytes());
        assertTrue(lastPath.endsWith("newtons-laws/ambiance.mp3"));
    }
    @Test void unknownEraIsRejected() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getScene("../stone-age")).getStatusCode().value());
    }
    @Test void missingRowReturns404() {
        replacement = "[]";
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getScene("sumerian-urban")).getStatusCode().value());
    }
    @Test void crossFolderPathRejectedBeforeDownloading() {
        replacement = "[{\"bucket_name\":\"animation-assets\",\"background_path\":\"stone-age/background.png\"}]";
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAsset("sumerian-urban", "background")).getStatusCode().value());
        assertEquals(0, downloads.get());
    }
    @Test void privateStorageFailureBecomes502() {
        storageStatus = 403;
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAsset("sumerian-urban", "audio")).getStatusCode().value());
    }
    @Test void newMp3PreferredForExistingDatabaseRow() {
        var audio = service.getAudio("sumerian-urban");
        assertEquals("audio/mpeg", audio.contentType());
        assertArrayEquals(BYTES, audio.bytes());
        assertTrue(lastPath.endsWith("sumerian-urban/ambience.mp3"));
        assertEquals(1, downloads.get());
    }
    @Test void missingMp3RetainsExistingWav() {
        mp3Missing = 404;
        assertEquals("audio/wav", service.getAudio("egypt-old-kingdom").contentType());
        assertTrue(lastPath.endsWith("ambience.wav"));
        assertEquals(2, downloads.get());
    }
    @Test void supabaseMissingObject400AlsoRetainsWav() {
        mp3Missing = 400;
        assertEquals("audio/wav", service.getAudio("indus-valley").contentType());
        assertEquals(2, downloads.get());
    }
    @Test void permissionFailureDoesNotSilentlyFallback() {
        storageStatus = 403;
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAudio("akkadian-empire")).getStatusCode().value());
        assertEquals(1, downloads.get());
    }
    @Test void audioPathCannotEscapeSceneFolder() {
        replacement = "[{\"bucket_name\":\"animation-assets\",\"settings\":{\"audio_path\":\"stone-age/ambience.mp3\"}}]";
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAudio("sumerian-urban")).getStatusCode().value());
        assertEquals(0, downloads.get());
    }

    @Test void foregroundLoadsFromSamePrivateEraFolder() {
        replacement = "[{\"id\":\"mauryan-empire\",\"bucket_name\":\"animation-assets\",\"settings\":{\"foreground_path\":\"mauryan-empire/five-foreground.png\"}}]";
        assertArrayEquals(BYTES, service.getAsset("mauryan-empire", "foreground"));
        assertTrue(lastPath.endsWith("/mauryan-empire/five-foreground.png"));
    }
    @Test void foregroundCannotUseAnotherErasPath() {
        replacement = "[{\"bucket_name\":\"animation-assets\",\"settings\":{\"foreground_path\":\"han-dynasty/five-foreground.png\"}}]";
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAsset("mauryan-empire", "foreground")).getStatusCode().value());
        assertEquals(0, downloads.get());
    }
    @Test void missingForegroundPathIsRejectedBeforeDownload() {
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.getAsset("mauryan-empire", "foreground")).getStatusCode().value());
        assertEquals(0, downloads.get());
    }
    @Test void wattSupportsPrivateAudioWithoutSceneImages() {
        replacement="[{\"id\":\"watt-condenser\",\"bucket_name\":\"animation-assets\",\"background_path\":\"\",\"characters_path\":\"\",\"settings\":{\"audio_path\":\"watt-condenser/ambiance.mp3\"}}]";
        assertEquals("audio/mpeg",service.getAudio("watt-condenser").contentType());
        assertTrue(lastPath.endsWith("watt-condenser/ambiance.mp3"));
    }
    @Test void modernBatchUsesSeparatePrivatePathsAndMp3PerEntry() {
        for(String id:List.of("declaration-independence","french-revolution","stockton-darlington","early-photography","darwin-evolution","emancipation-proclamation","bell-telephone","edison-light","wright-flight","first-world-war","second-world-war","united-nations","civil-rights","moon-landing","web-software","first-generation-computers","september-11","financial-crisis","covid-19")) {
            replacement="[{\"id\":\""+id+"\",\"bucket_name\":\"animation-assets\",\"background_path\":\""+id+"/background.png\",\"characters_path\":\""+id+"/actors.png\",\"settings\":{\"foreground_path\":\""+id+"/document.png\",\"map_path\":\""+id+"/map.png\",\"audio_path\":\""+id+"/ambiance.mp3\"}}]";
            assertEquals(id,service.getScene(id).get("id"));
            assertArrayEquals(BYTES,service.getAsset(id,"foreground"));
            assertTrue(lastPath.endsWith(id+"/document.png"));
            assertArrayEquals(BYTES,service.getAsset(id,"map"));
            assertTrue(lastPath.endsWith(id+"/map.png"));
            assertArrayEquals(BYTES,service.getAudio(id).bytes());
            assertTrue(lastPath.endsWith(id+"/ambiance.mp3"));
        }
    }
    @Test void signingDocumentAndAudienceUseDifferentObjects() {
        replacement="[{\"id\":\"emancipation-proclamation\",\"bucket_name\":\"animation-assets\",\"settings\":{\"document_path\":\"emancipation-proclamation/document.png\",\"foreground_path\":\"emancipation-proclamation/audience.png\"}}]";
        assertArrayEquals(BYTES,service.getAsset("emancipation-proclamation","document"));
        assertTrue(lastPath.endsWith("emancipation-proclamation/document.png"));
        assertArrayEquals(BYTES,service.getAsset("emancipation-proclamation","foreground"));
        assertTrue(lastPath.endsWith("emancipation-proclamation/audience.png"));
    }
}
