package com.historyinaglimpse.animation_backend.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Google sign-in for the desktop app. The app opens Google's sign-in page in
 * the browser and receives a one-time code; the backend trades that code for
 * the user's Google identity here, so the client secret never leaves the backend.
 *
 * Turned off unless google.client-id and google.client-secret are set.
 */
@Component
public class GoogleSignIn {
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String tokenInfoUrl;
    private final RestClient client = RestClient.create();

    public GoogleSignIn(@Value("${google.client-id:}") String clientId,
                        @Value("${google.client-secret:}") String clientSecret,
                        @Value("${google.token-url:https://oauth2.googleapis.com/token}") String tokenUrl,
                        @Value("${google.tokeninfo-url:https://oauth2.googleapis.com/tokeninfo}") String tokenInfoUrl) {
        this.clientId = clientId == null ? "" : clientId.strip();
        this.clientSecret = clientSecret == null ? "" : clientSecret.strip();
        this.tokenUrl = tokenUrl;
        this.tokenInfoUrl = tokenInfoUrl;
    }

    /** Who Google says signed in. */
    public record GoogleUser(String sub, String email, String name) { }

    public boolean enabled() { return !clientId.isEmpty() && !clientSecret.isEmpty(); }

    /** Safe to share: the desktop app needs it to build the sign-in link. */
    public String clientId() { return clientId; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenReply(@JsonProperty("id_token") String idToken) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInfo(String aud, String iss, String sub, String email,
                     @JsonProperty("email_verified") String emailVerified, String name) { }

    /** Trades a sign-in code for the Google user, or throws 401 if Google does not vouch for it. */
    public GoogleUser exchange(String code, String codeVerifier, String redirectUri) {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Google sign-in is not set up.");
        if (blank(code) || blank(codeVerifier) || blank(redirectUri))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Google sign-in was incomplete.");
        // Desktop apps receive the code on a loopback address only.
        if (!redirectUri.startsWith("http://127.0.0.1:") && !redirectUri.startsWith("http://localhost:"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Google sign-in was incomplete.");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        TokenReply token;
        try {
            token = client.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(TokenReply.class);
        } catch (RestClientResponseException ex) {
            throw rejected();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Google.");
        }
        if (token == null || blank(token.idToken())) throw rejected();

        // Google checks the token's signature and expiry; we check it was issued to this app.
        TokenInfo info;
        try {
            info = client.get().uri(tokenInfoUrl + "?id_token="
                    + UriUtils.encodeQueryParam(token.idToken(), StandardCharsets.UTF_8))
                    .retrieve().body(TokenInfo.class);
        } catch (RestClientResponseException ex) {
            throw rejected();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Google.");
        }
        if (info == null || !clientId.equals(info.aud()) || !ISSUERS.contains(info.iss()) || blank(info.sub()))
            throw rejected();
        if (!"true".equals(info.emailVerified()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verify your Google email address first.");
        return new GoogleUser(info.sub(), info.email(), info.name());
    }

    private static ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in did not go through. Try again.");
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
