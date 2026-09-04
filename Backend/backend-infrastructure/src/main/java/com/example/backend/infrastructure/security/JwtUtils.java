package com.example.backend.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Optional;

@Component
public class JwtUtils {
    private static final String TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String CHAT_SESSION_TOKEN = "chat_session";
    private static final String AGENT_CAPABILITY_TOKEN = "agent_capability";
    private static final long CAPABILITY_EXPIRATION_MS = 5 * 60 * 1000L;
    private final SecretKey key;
    private final long expirationTime;

    public JwtUtils(@Value("${security.jwt.secret}") String secret,
                    @Value("${security.jwt.expiration-ms:86400000}") long expirationTime) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationTime = expirationTime;
    }

    public String generateToken(String username, Set<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim(TOKEN_TYPE, ACCESS_TOKEN)
                .claim("roles", roles.stream().toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRolesFromToken(String token) {
        List<String> rolesList = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("roles", List.class);
        return rolesList == null ? Set.of() : Set.copyOf(rolesList);
    }

    public boolean validateToken(String token) {
        try {
            return ACCESS_TOKEN.equals(parseClaims(token).get(TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String generateChatSessionToken(String sessionId) {
        return Jwts.builder()
                .subject(sessionId)
                .claim(TOKEN_TYPE, CHAT_SESSION_TOKEN)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key)
                .compact();
    }

    public boolean validateChatSessionToken(String token, String sessionId) {
        try {
            Claims claims = parseClaims(token);
            return CHAT_SESSION_TOKEN.equals(claims.get(TOKEN_TYPE, String.class))
                    && sessionId.equals(claims.getSubject());
        } catch (Exception e) {
            return false;
        }
    }

    public String generateAgentCapabilityToken(String username, String sessionId, Set<String> scopes) {
        return Jwts.builder()
                .subject(username)
                .claim(TOKEN_TYPE, AGENT_CAPABILITY_TOKEN)
                .claim("session_id", sessionId)
                .claim("scopes", scopes.stream().sorted().toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + CAPABILITY_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public Optional<AgentCapability> parseAgentCapabilityToken(String token, String requiredScope) {
        try {
            Claims claims = parseClaims(token);
            List<String> scopes = claims.get("scopes", List.class);
            if (!AGENT_CAPABILITY_TOKEN.equals(claims.get(TOKEN_TYPE, String.class))
                    || scopes == null || !scopes.contains(requiredScope)) {
                return Optional.empty();
            }
            return Optional.of(new AgentCapability(
                    claims.getSubject(),
                    claims.get("session_id", String.class),
                    Set.copyOf(scopes)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record AgentCapability(String username, String sessionId, Set<String> scopes) {}

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
