package com.example.ssl.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;

@Component
public class JwtValidator {

    @Value("${casdoor.base-url}")
    private String casdoorBaseUrl;

    @Value("${casdoor.client-id}")
    private String clientId;

    @Value("${casdoor.jwks-uri}")
    private String jwksUri;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public JsonNode validateJwtAndGetClaims(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");

        if (parts.length != 3) {
            throw new RuntimeException("Invalid JWT format");
        }

        String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
        );

        String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        );

        JsonNode header = objectMapper.readTree(headerJson);
        JsonNode claims = objectMapper.readTree(payloadJson);

        String alg = header.has("alg") ? header.get("alg").asText() : null;
        if (!"RS256".equals(alg)) {
            throw new RuntimeException("Unsupported JWT algorithm");
        }

        String kid = header.has("kid") ? header.get("kid").asText() : null;
        if (kid == null || kid.isBlank()) {
            throw new RuntimeException("JWT kid is missing");
        }

        PublicKey publicKey = getPublicKeyFromJwks(kid);

        String signedData = parts[0] + "." + parts[1];
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(signedData.getBytes(StandardCharsets.UTF_8));

        if (!signature.verify(signatureBytes)) {
            throw new RuntimeException("Invalid JWT signature");
        }

        validateClaims(claims);

        return claims;
    }

    private PublicKey getPublicKeyFromJwks(String kid) throws Exception {
        HttpRequest jwksRequest = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .build();

        HttpResponse<String> jwksResponse =
                httpClient.send(jwksRequest, HttpResponse.BodyHandlers.ofString());

        if (jwksResponse.statusCode() != 200) {
            throw new RuntimeException("Cannot load JWKS");
        }

        JsonNode jwks = objectMapper.readTree(jwksResponse.body());
        JsonNode keys = jwks.get("keys");

        if (keys == null || !keys.isArray()) {
            throw new RuntimeException("Invalid JWKS");
        }

        for (JsonNode key : keys) {
            String keyId = key.has("kid") ? key.get("kid").asText() : "";

            if (kid.equals(keyId)) {
                String n = key.get("n").asText();
                String e = key.get("e").asText();

                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                return keyFactory.generatePublic(spec);
            }
        }

        throw new RuntimeException("Public key was not found");
    }

    private void validateClaims(JsonNode claims) {
        long now = System.currentTimeMillis() / 1000;

        if (!claims.has("exp")) {
            throw new RuntimeException("exp claim is missing");
        }

        if (claims.get("exp").asLong() < now) {
            throw new RuntimeException("JWT is expired");
        }

        if (claims.has("nbf") && claims.get("nbf").asLong() > now) {
            throw new RuntimeException("JWT is not active yet");
        }

        if (!claims.has("iss")) {
            throw new RuntimeException("iss claim is missing");
        }

        String issuer = claims.get("iss").asText();

        if (!issuer.equals(casdoorBaseUrl)) {
            throw new RuntimeException("Invalid issuer");
        }

        if (!claims.has("aud")) {
            throw new RuntimeException("aud claim is missing");
        }

        JsonNode aud = claims.get("aud");
        boolean validAudience = false;

        if (aud.isTextual()) {
            validAudience = clientId.equals(aud.asText());
        } else if (aud.isArray()) {
            for (JsonNode item : aud) {
                if (clientId.equals(item.asText())) {
                    validAudience = true;
                    break;
                }
            }
        }

        if (!validAudience) {
            throw new RuntimeException("Invalid audience");
        }
    }
}