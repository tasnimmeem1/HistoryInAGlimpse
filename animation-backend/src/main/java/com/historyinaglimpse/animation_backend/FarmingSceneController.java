package com.historyinaglimpse.animation_backend;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class FarmingSceneController {
    private final FarmingSceneService service;
    public FarmingSceneController(FarmingSceneService service) { this.service = service; }
    @GetMapping(value = "/api/scenes/early-farming", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getScene() { return service.getScene(); }
    @GetMapping(value = "/api/scenes/early-farming/background", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getBackground() { return service.getImage("background"); }
    @GetMapping(value = "/api/scenes/early-farming/characters", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getCharacters() { return service.getImage("characters"); }
    @GetMapping(value = "/api/scenes/early-farming/audio", produces = "audio/mpeg")
    public byte[] getAudio() { return service.getAudio(); }

}
