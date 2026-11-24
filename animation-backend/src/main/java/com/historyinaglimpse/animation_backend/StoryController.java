package com.historyinaglimpse.animation_backend;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/stories", "/api/contributions"})
public class StoryController {
    private final StoryService stories;

    public StoryController(StoryService stories) {
        this.stories = stories;
    }

    public record StoryRequest(String title, String content, String tags) { }

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
}
