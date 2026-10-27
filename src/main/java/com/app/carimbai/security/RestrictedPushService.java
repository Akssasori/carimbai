package com.app.carimbai.security;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.jose4j.lang.JoseException;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class RestrictedPushService extends PushService {
    private final PushEndpointPolicy policy;

    public RestrictedPushService(String publicKey, String privateKey, String subject, PushEndpointPolicy policy)
            throws GeneralSecurityException {
        super(publicKey, privateKey, subject);
        this.policy = policy;
    }

    @Override
    public HttpPost preparePost(Notification notification, Encoding encoding)
            throws GeneralSecurityException, IOException, JoseException {
        HttpPost post = super.preparePost(notification, encoding);
        policy.validateDestination(post.getURI().toString());
        post.setConfig(RequestConfig.custom().setRedirectsEnabled(false)
                .setConnectTimeout(5000).setSocketTimeout(5000).setConnectionRequestTimeout(5000).build());
        return post;
    }
}
