package com.historyinaglimpse.animation_backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupabaseSceneService {

    private final RestClient client;

    public SupabaseSceneService(
            @Value("${supabase.url}") String url,
            @Value("${supabase.secret-key}") String key) {

        if (key.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_SECRET_KEY is missing.");
        }

        client = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("apikey", key)
                .build();
    }

    public String getStoneAgeScene() {
        try {
            String result = client.get()
                    .uri("/rest/v1/animation_scenes"
                            + "?id=eq.stone-age&select=*")

                    .retrieve()
                    .body(String.class);

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Supabase returned no response.");
            }

            return result;

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not load the scene from Supabase.");
        }
    }
    public byte[] getStoneAgeBackground() {
        try {
            byte[] image = client.get()
                    .uri("/storage/v1/object/authenticated/"
                            + "animation-assets/"
                            + "stone-age/hunter-camp-background.png")
                    .retrieve()
                    .body(byte[].class);

            if (image == null || image.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The background image is empty.");
            }

            return image;

        } catch (org.springframework.web.client.RestClientResponseException exception) {
            System.err.println("Supabase Storage status: "
                    + exception.getStatusCode());
            System.err.println("Supabase Storage error: "
                    + exception.getResponseBodyAsString());

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not load the private background image.");

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not connect to Supabase Storage.");
        }
    }
    public byte[] getStoneAgeCharacters() {
        return downloadStoneAgeImage("hunter-individual-poses.png");
    }

    public byte[] getStoneAgeFire() {
        return downloadStoneAgeImage("hunter-fire-poses.png");
    }

    private byte[] downloadStoneAgeImage(String filename) {
        try {
            byte[] image = client.get()
                    .uri("/storage/v1/object/authenticated/"
                            + "animation-assets/stone-age/" + filename)
                    .retrieve()
                    .body(byte[].class);

            if (image == null || image.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The image is empty.");
            }

            return image;

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not load the animation image.");
        }
    }
    public byte[] getStoneAgeBirds() {
        try {
            byte[] audio = client.get()
                    .uri("/storage/v1/object/authenticated/"
                            + "animation-assets/"
                            + "stone-age/stone-age-birds.mp3")
                    .retrieve()
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The bird audio is empty."
                );
            }

            return audio;

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not load the bird audio."
            );
        }
    }
}