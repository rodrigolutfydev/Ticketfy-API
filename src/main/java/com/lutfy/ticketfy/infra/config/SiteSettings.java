package com.lutfy.ticketfy.infra.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Component
public class SiteSettings {

    private final String baseUrl;

    @Autowired
    public SiteSettings(@Value("${ticketfy.site.base-url:}") String baseUrl, Environment environment) {
        this(baseUrl, environment.acceptsProfiles(Profiles.of("prod")));
    }

    public SiteSettings(String baseUrl, boolean production) {
        var trimmed = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (!trimmed.isEmpty()) {
            URI uri;
            try {
                uri = URI.create(trimmed);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("ticketfy.site.base-url must be an absolute URL", ex);
            }
            var scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalStateException("ticketfy.site.base-url must be an absolute http(s) URL");
            }
            if (production && !scheme.equals("https")) {
                throw new IllegalStateException("ticketfy.site.base-url must use https in production");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException("ticketfy.site.base-url must not have a query or fragment");
            }
        }
        this.baseUrl = trimmed;
    }

    public Optional<String> eventUrl(UUID eventId) {
        return baseUrl.isEmpty() ? Optional.empty() : Optional.of(baseUrl + "/events/" + eventId);
    }
}
