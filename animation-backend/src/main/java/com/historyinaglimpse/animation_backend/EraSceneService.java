package com.historyinaglimpse.animation_backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Metadata and private media for the cloud animation catalog. */
@Service
public class EraSceneService {
    private static final Set<String> IDS = Set.of("sumerian-urban", "egypt-old-kingdom", "indus-valley",
            "akkadian-empire", "egypt-middle-kingdom",
            "hittite-kingdom", "shang-dynasty", "egypt-new-kingdom", "phoenician-network", "zhou-dynasty", "neo-assyrian-empire", "archaic-greece", "neo-babylonian-period", "achaemenid-persia", "classical-greece",
            "mauryan-empire", "parthian-empire", "qin-dynasty", "han-dynasty", "western-imperial-rome", "aksumite-empire", "sasanian-empire", "classic-maya", "eastern-roman-empire", "ghana-wagadu", "gupta-empire", "tang-dynasty", "angkor-empire", "abbasid-caliphate", "song-dynasty", "mali-empire", "ottoman-empire", "ming-dynasty", "aztec-empire", "inca-expansion", "songhai-empire", "mughal-dynasty", "tokugawa-shogunate", "qing-dynasty", "royal-society", "newtons-laws", "watt-condenser", "declaration-independence", "french-revolution", "stockton-darlington", "early-photography", "darwin-evolution", "emancipation-proclamation", "bell-telephone", "edison-light", "wright-flight", "first-world-war", "second-world-war", "united-nations", "civil-rights", "moon-landing", "web-software", "first-generation-computers", "september-11", "financial-crisis", "covid-19");
    private final RestClient client;
    public EraSceneService(@Value("${supabase.url}") String url, @Value("${supabase.secret-key}") String key) {
        client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
    }
    public Map<String, Object> getScene(String id) {
        if (!IDS.contains(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown era.");
        try {
            List<Map<String, Object>> rows = client.get().uri("/rest/v1/animation_scenes?id=eq." + id + "&select=*")
                    .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (rows == null || rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Era not set up.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the era.");
        }
    }
    public byte[] getAsset(String id, String kind) {
        Map<String, Object> scene = getScene(id);
        Object rawSettings = scene.get("settings");
        Map<?, ?> settings = rawSettings instanceof Map<?, ?> value ? value : Map.of();
        String field = switch (kind) {
            case "background" -> "background_path";
            case "characters" -> "characters_path";
            case "map" -> "map_path";
            case "document" -> "document_path";
            case "foreground" -> "foreground_path";
            case "audio" -> "audio_path";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        Object raw = kind.equals("background") || kind.equals("characters") ? scene.get(field) : settings.get(field);
        String path = raw == null ? "" : raw.toString();
        String extension = kind.equals("audio") ? "wav" : "png";
        if (!"animation-assets".equals(scene.get("bucket_name"))
                || !path.matches(id + "/[a-z0-9-]+\\." + extension))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid era asset path.");
        try {
            byte[] bytes = client.get().uri("/storage/v1/object/authenticated/animation-assets/" + path)
                    .retrieve().body(byte[].class);
            int limit = kind.equals("audio") ? 50 * 1024 * 1024 : 12 * 1024 * 1024;
            if (bytes == null || bytes.length == 0 || bytes.length > limit)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid era asset size.");
            return bytes;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load private era media.");
        }
    }
    public record AudioAsset(byte[] bytes, String contentType) {}

    /** Prefer the user's new MP3 while retaining existing WAV-only scenes. */
    public AudioAsset getAudio(String id) {
        Map<String, Object> scene = getScene(id);
        Map<?, ?> settings = scene.get("settings") instanceof Map<?, ?> value ? value : Map.of();
        String configured = String.valueOf(settings.get("audio_path"));
        if (!"animation-assets".equals(scene.get("bucket_name"))
                || !configured.matches(id + "/[a-z0-9-]+\\.(mp3|wav)"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid era audio path.");
        List<String> candidates = configured.equals(id + "/ambience.wav")
                ? List.of(id + "/ambience.mp3", configured) : List.of(configured);
        for (int n = 0; n < candidates.size(); n++) {
            String path = candidates.get(n);
            try {
                byte[] bytes = client.get().uri("/storage/v1/object/authenticated/animation-assets/" + path)
                        .retrieve().body(byte[].class);
                if (bytes == null || bytes.length == 0 || bytes.length > 50 * 1024 * 1024)
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid era audio size.");
                return new AudioAsset(bytes, path.endsWith(".wav") ? "audio/wav" : "audio/mpeg");
            } catch (RestClientResponseException ex) {
                boolean missing = ex.getStatusCode().value() == 404
                        || (ex.getStatusCode().value() == 400 && ex.getResponseBodyAsString().contains("Object not found"));
                if (missing && n + 1 < candidates.size()) continue;
                System.err.println("Era audio storage failed: " + path + " HTTP " + ex.getStatusCode().value());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the era sound: " + path);
            } catch (RestClientException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the era sound.");
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The era sound is missing.");
    }

}
