package com.historyinaglimpse.animation_backend;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnimationSceneController {

    private final SupabaseSceneService service;

    public AnimationSceneController(SupabaseSceneService service) {
        this.service = service;
    }

    @GetMapping(
            value = "/api/scenes/stone-age",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public String getStoneAgeScene() {
        return service.getStoneAgeScene();
    }
    @GetMapping(
            value = "/api/scenes/stone-age/background",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public byte[] getStoneAgeBackground() {
        return service.getStoneAgeBackground();
    }
    @GetMapping(
            value = "/api/scenes/stone-age/characters",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public byte[] getStoneAgeCharacters() {
        return service.getStoneAgeCharacters();
    }

    @GetMapping(
            value = "/api/scenes/stone-age/fire",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public byte[] getStoneAgeFire() {
        return service.getStoneAgeFire();
    }
    @GetMapping(
            value = "/api/scenes/stone-age/birds",
            produces = "audio/mpeg"
    )
    public byte[] getStoneAgeBirds() {
        return service.getStoneAgeBirds();
    }
}