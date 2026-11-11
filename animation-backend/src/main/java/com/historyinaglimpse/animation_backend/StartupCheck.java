package com.historyinaglimpse.animation_backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Says in the run window when the backend is ready, so people know when to start the desktop app. */
@Component
class StartupCheck {
    private static final Logger LOG = LoggerFactory.getLogger(StartupCheck.class);
    private final String address;
    private final int port;

    StartupCheck(@Value("${server.address}") String address, @Value("${server.port}") int port) {
        this.address = address;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        LOG.info("Backend ready on http://{}:{}. Now start the desktop app.", address, port);
    }
}
