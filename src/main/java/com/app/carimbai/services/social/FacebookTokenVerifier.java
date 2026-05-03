package com.app.carimbai.services.social;

import com.app.carimbai.config.OAuthProperties;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.InvalidSocialTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class FacebookTokenVerifier implements SocialTokenVerifier {

    private static final String GRAPH_URL = "https://graph.facebook.com/me?fields=id,name,email&access_token=%s&appsecret_proof=%s";

    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FacebookTokenVerifier(OAuthProperties props) {
        this.appSecret = props.facebook() != null ? props.facebook().appSecret() : null;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.FACEBOOK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifiedSocialIdentity verify(String token) {
        try {
            String proof = computeAppSecretProof(token);
            String url = GRAPH_URL.formatted(token, proof);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Facebook Graph API retornou {}: {}", response.statusCode(), response.body());
                throw new InvalidSocialTokenException("Token do Facebook inválido");
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);

            if (body.containsKey("error")) {
                throw new InvalidSocialTokenException("Token do Facebook inválido: " + body.get("error"));
            }

            String id = (String) body.get("id");
            if (id == null) {
                throw new InvalidSocialTokenException("Token do Facebook não retornou id");
            }

            return new VerifiedSocialIdentity(
                    id,
                    (String) body.get("email"),
                    (String) body.get("name")
            );

        } catch (InvalidSocialTokenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao verificar Facebook token: {}", e.getMessage());
            throw new InvalidSocialTokenException("Falha ao verificar token do Facebook");
        }
    }

    private String computeAppSecretProof(String accessToken) {
        if (appSecret == null || appSecret.isBlank()) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Falha ao calcular appsecret_proof: {}", e.getMessage());
            return "";
        }
    }
}
