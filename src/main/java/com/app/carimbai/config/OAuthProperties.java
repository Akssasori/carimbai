package com.app.carimbai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carimbai.oauth")
public record OAuthProperties(
        Google google,
        Apple apple,
        Facebook facebook
) {
    public record Google(String clientId) {}
    public record Apple(String clientId, String teamId) {}
    public record Facebook(String appId, String appSecret) {}
}
