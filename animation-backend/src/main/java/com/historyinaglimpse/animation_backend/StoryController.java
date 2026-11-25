package com.historyinaglimpse.animation_backend;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/stories", "/api/contributions"})
public class StoryController {
    private final StoryService stories;

    public StoryController(StoryService stories) {
        this.stories = stories;
    }

    public record StoryRequest(String title, String content, String tags,
                               String historicalDate, String placeName, Double latitude, Double longitude) { }

    public record ImageUpload(String contentType, byte[] data) { }

    @GetMapping
    public List<Story> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return stories.list(authorization);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Story create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody StoryRequest body) {
        return stories.create(authorization, body);
    }

    @PutMapping("/{id}")
    public Story update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long id,
            @RequestBody StoryRequest body) {
        return stories.update(authorization, id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long id) {
        stories.delete(authorization, id);
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Story uploadImage(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long id,
            @RequestBody ImageUpload image) {
        return stories.uploadImage(authorization, id, image.contentType(), image.data());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long id) {
        StoryService.StoryImage image = stories.image(authorization, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.data());
    }

    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long id) {
        stories.removeImage(authorization, id);
    }
}
