package com.historyinaglimpse.animation_backend.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Google sign-in against a fake Google token server. */
class GoogleSignInTests {
    private HttpServer server;
    private String base;
    private final List<String> forms = new ArrayList<>();
    private volatile String tokenInfo;
    private volatile int tokenStatus = 200;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", e -> {
            forms.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(e, tokenStatus, tokenStatus == 200 ? "{\"id_token\":\"abc.def.ghi\",\"access_token\":\"x\"}"
                    : "{\"error\":\"invalid_grant\"}");
        });
        server.createContext("/tokeninfo", e -> {
            assertEquals("id_token=abc.def.ghi", e.getRequestURI().getRawQuery());
            reply(e, 200, tokenInfo);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        tokenInfo = info("app-id", "true");
    }

    @AfterEach void stop() { server.stop(0); }

    private static void reply(com.sun.net.httpserver.HttpExchange e, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    private static String info(String aud, String verified) {
        return "{\"aud\":\"" + aud + "\",\"iss\":\"https://accounts.google.com\",\"sub\":\"1234567890\","
                + "\"email\":\"Ada.Lovelace@gmail.com\",\"email_verified\":\"" + verified + "\",\"name\":\"Ada Lovelace\"}";
    }

    private GoogleSignIn google(String id, String secret) {
        return new GoogleSignIn(id, secret, base + "/token", base + "/tokeninfo");
    }

    private static HttpStatus status(Runnable call) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
    }

    @Test void turnedOffWithoutClientIdAndSecret() {
        assertFalse(google("", "").enabled());
        assertFalse(google("app-id", "").enabled());
        assertTrue(google("app-id", "secret").enabled());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> google("", "").exchange("c", "v", "http://127.0.0.1:5000/")));
    }

    @Test void tradesCodeForGoogleUser() {
        var user = google("app-id", "secret").exchange("the-code", "the-verifier", "http://127.0.0.1:5000/");
        assertEquals(new GoogleSignIn.GoogleUser("1234567890", "Ada.Lovelace@gmail.com", "Ada Lovelace"), user);
        String form = forms.get(0);
        assertTrue(form.contains("grant_type=authorization_code"), form);
        assertTrue(form.contains("code=the-code"), form);
        assertTrue(form.contains("code_verifier=the-verifier"), form);
        assertTrue(form.contains("client_secret=secret"), form);
    }

    @Test void refusesTokensForOtherAppsAndUnverifiedEmails() {
        tokenInfo = info("someone-elses-app", "true");
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> google("app-id", "secret").exchange("c", "v", "http://127.0.0.1:5000/")));
        tokenInfo = info("app-id", "false");
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> google("app-id", "secret").exchange("c", "v", "http://127.0.0.1:5000/")));
    }

    @Test void refusesBadCodesAndNonLoopbackRedirects() {
        tokenStatus = 400;
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> google("app-id", "secret").exchange("c", "v", "http://127.0.0.1:5000/")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> google("app-id", "secret").exchange("c", "v", "https://evil.example/")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> google("app-id", "secret").exchange("", "v", "http://127.0.0.1:5000/")));
    }

    @Test void firstGoogleLoginMakesAnAccountThenReusesIt() {
        var store = new AuthServiceTests.MemoryStore();
        var auth = new AuthService(store);
        auth.register("ada_lovelace", null, "longenough");   // takes the obvious username

        var first = auth.googleLogin(new GoogleSignIn.GoogleUser("g-1", "Ada.Lovelace@gmail.com", "Ada Lovelace"));
        assertEquals("ada_lovelace2", first.user().username());
        assertEquals("Ada Lovelace", first.user().displayName());
        assertEquals(Role.USER, first.user().role());
        assertTrue(first.user().google());
        assertFalse(first.user().hasPassword());

        var again = auth.googleLogin(new GoogleSignIn.GoogleUser("g-1", "renamed@gmail.com", "Someone"));
        assertEquals("ada_lovelace2", again.user().username());
        assertNotEquals(first.token(), again.token());
        assertEquals(2, store.rows.size());

        // No password yet, so password login fails until the user adds one.
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.login("ada_lovelace2", "")));
        auth.changePassword("Bearer " + again.token(), null, "brandnew123");
        assertTrue(auth.requireUser("Bearer " + again.token()).hasPassword());
        assertNotNull(auth.login("ada_lovelace2", "brandnew123").token());
    }

    @Test void shortOrOddEmailsStillGiveValidUsernames() {
        var auth = new AuthService(new AuthServiceTests.MemoryStore());
        assertEquals("jo_user", auth.googleLogin(new GoogleSignIn.GoogleUser("g-2", "jo@x.org", null)).user().username());
        assertEquals("first_last", auth.googleLogin(new GoogleSignIn.GoogleUser("g-3", "--First.Last--@x.org", "")).user().username());
        String longName = auth.googleLogin(new GoogleSignIn.GoogleUser("g-4", "a".repeat(40) + "@x.org", "A")).user().username();
        assertTrue(longName.matches("[a-z0-9_]{3,30}"), longName);
    }
}
