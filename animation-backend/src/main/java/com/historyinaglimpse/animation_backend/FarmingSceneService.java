package com.historyinaglimpse.animation_backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** Reads the farming layout and private image paths from Supabase, not the desktop. */
@Service
public class FarmingSceneService {
    private final RestClient client;
    public FarmingSceneService(@Value("${supabase.url}") String url,
                               @Value("${supabase.secret-key}") String key) {
        client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
    }
    public Map<String, Object> getScene() {
        try {
            List<Map<String, Object>> rows = client.get()
                    .uri("/rest/v1/animation_scenes?id=eq.early-farming&select=*")
                    .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (rows == null || rows.isEmpty()) throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "The farming scene has not been set up.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the farming scene.");
        }
    }
    public byte[] getImage(String kind) {
        String column = switch (kind) {
            case "background" -> "background_path";
            case "characters" -> "characters_path";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        Map<String, Object> scene = getScene();
        String bucket = String.valueOf(scene.get("bucket_name"));
        String path = String.valueOf(scene.get(column));
        if (!bucket.equals("animation-assets") || !path.matches("early-farming/[a-z0-9-]+\\.png"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid farming asset path.");
        try {
            byte[] image = client.get().uri("/storage/v1/object/authenticated/" + bucket + "/" + path)
                    .retrieve().body(byte[].class);
            if (image == null || image.length == 0) throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The farming image is empty.");
            return image;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the farming image.");
        }
    }
    /** Reads the dedicated farming MP3 from private storage. */
    public byte[] getAudio() {
        Map<String, Object> scene = getScene();
        Object rawSettings = scene.get("settings");
        Object rawPath = rawSettings instanceof Map<?, ?> settings ? settings.get("audio_path") : null;
        String path = rawPath == null ? "early-farming/farming-ambience.mp3" : rawPath.toString();
        // Older scene rows used the shared birds. Upgrade without requiring a SQL migration.
        if (path.equals("stone-age/stone-age-birds.mp3")) path = "early-farming/farming-ambience.mp3";
        if (!"animation-assets".equals(scene.get("bucket_name"))
                || !path.matches("early-farming/[a-zA-Z0-9_-]+\\.mp3"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid farming audio path.");
        try {
            byte[] audio = client.get().uri("/storage/v1/object/authenticated/animation-assets/" + path)
                    .retrieve().body(byte[].class);
            if (audio == null || audio.length == 0) throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The farming audio is empty.");
            return audio;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load the farming audio.");
        }
    }

}
