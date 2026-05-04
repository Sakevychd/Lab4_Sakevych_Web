package com.example.ssl;

import com.example.ssl.jwt.JwtValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Controller
class PageController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}

@RestController
public class HelloController {

    @Value("${casdoor.base-url}")
    private String casdoorBaseUrl;

    @Value("${casdoor.client-id}")
    private String clientId;

    @Value("${casdoor.client-secret}")
    private String clientSecret;

    @Value("${casdoor.redirect-uri}")
    private String redirectUri;

    @Value("${casdoor.scope}")
    private String scope;

    private final JwtValidator jwtValidator;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public HelloController(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String state = generateRandomValue();
        String nonce = generateRandomValue();

        addCookie(response, "oidc_state", state, 300, true);
        addCookie(response, "oidc_nonce", nonce, 300, true);

        String authorizationUrl = casdoorBaseUrl + "/login/oauth/authorize"
                + "?client_id=" + encode(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + "&state=" + encode(state)
                + "&nonce=" + encode(nonce);

        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/callback")
    public void callback(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String code = request.getParameter("code");
        String returnedState = request.getParameter("state");
        String savedState = getCookieValue(request, "oidc_state");

        if (code == null || code.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authorization code is missing");
            return;
        }

        if (returnedState == null || savedState == null || !returnedState.equals(savedState)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid state parameter");
            return;
        }

        String tokenResponse = exchangeCodeForToken(code);
        JsonNode tokenJson = objectMapper.readTree(tokenResponse);

        if (!tokenJson.has("access_token")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Access token was not received");
            return;
        }

        String accessToken = tokenJson.get("access_token").asText();

        // false потрібно, щоб frontend міг прочитати cookie і підставити token у WebSocket URL
        addCookie(response, "access_token", accessToken, 3600, false);

        response.sendRedirect("/");
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> userInfo(HttpServletRequest request) {
        String accessToken = getCookieValue(request, "access_token");

        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing access token");
        }

        try {
            JsonNode claims = jwtValidator.validateJwtAndGetClaims(accessToken);

            return ResponseEntity.ok(Map.of(
                    "jwtValidation", "JWT signature was validated locally on backend",
                    "tokenSource", "access_token from cookie",
                    "algorithm", "RS256",
                    "claims", claims
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid JWT token: " + e.getMessage());
        }
    }

    private String exchangeCodeForToken(String code) throws Exception {
        String body = "grant_type=authorization_code"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(casdoorBaseUrl + "/api/login/oauth/access_token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> tokenResponse =
                httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

        if (tokenResponse.statusCode() != 200) {
            throw new RuntimeException("Token endpoint returned status: " + tokenResponse.statusCode());
        }

        return tokenResponse.body();
    }

    private void addCookie(HttpServletResponse response,
                           String name,
                           String value,
                           int maxAge,
                           boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String generateRandomValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}