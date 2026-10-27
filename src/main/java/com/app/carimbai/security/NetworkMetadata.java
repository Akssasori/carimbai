package com.app.carimbai.security;

import com.app.carimbai.config.PrivacyProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.InetAddress;

@Component @RequiredArgsConstructor
public class NetworkMetadata {
    private final PrivacyProperties settings;
    public InetAddress ip(HttpServletRequest request) {
        if (!settings.isCollectNetworkMetadata() || request==null) return null;
        try {
            String value=request.getRemoteAddr();
            if (value==null || !(org.apache.http.conn.util.InetAddressUtils.isIPv4Address(value)
                    || org.apache.http.conn.util.InetAddressUtils.isIPv6Address(value))) return null;
            return InetAddress.getByName(value);
        } catch (java.net.UnknownHostException | IllegalStateException ex) { return null; }
    }
    public String userAgent(String value) {
        if (!settings.isCollectNetworkMetadata() || value==null) return null;
        String clean=value.replaceAll("[\\p{Cntrl}]","");
        return clean.substring(0,Math.min(256,clean.length()));
    }
}
