package com.historyinaglimpse.animation_backend;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class EraSceneController {
    private final EraSceneService service;
    public EraSceneController(EraSceneService service) { this.service = service; }
    @GetMapping(value="/api/era-scenes/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> scene(@PathVariable("id") String id) { return service.getScene(id); }
    @GetMapping(value="/api/era-scenes/{id}/background", produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] background(@PathVariable("id") String id) { return service.getAsset(id, "background"); }
    @GetMapping(value="/api/era-scenes/{id}/characters", produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] characters(@PathVariable("id") String id) { return service.getAsset(id, "characters"); }
    @GetMapping(value="/api/era-scenes/{id}/foreground", produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] foreground(@PathVariable("id") String id) { return service.getAsset(id, "foreground"); }
    @GetMapping(value="/api/era-scenes/{id}/document", produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] document(@PathVariable("id") String id) { return service.getAsset(id, "document"); }
    @GetMapping(value="/api/era-scenes/{id}/map", produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] map(@PathVariable("id") String id) { return service.getAsset(id, "map"); }
    @GetMapping(value="/api/era-scenes/{id}/audio.wav", produces="audio/wav")
    public byte[] audio(@PathVariable("id") String id) { return service.getAsset(id, "audio"); }
    @GetMapping("/api/era-scenes/{id}/audio")
    public ResponseEntity<byte[]> flexibleAudio(@PathVariable("id") String id) {
        EraSceneService.AudioAsset audio = service.getAudio(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.contentType())).body(audio.bytes());
    }

}
