package com.lutfy.ticketfy.infra.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteSettingsTest {

    private static final UUID ID = UUID.fromString("3f0b6c1e-8a5d-4f7e-9c2b-1d4e5f6a7b8c");

    @Test
    void buildsTheEventLinkWithoutDuplicatedSlashes() {
        assertThat(new SiteSettings("https://ticketfy.example/", true).eventUrl(ID))
                .contains("https://ticketfy.example/events/" + ID);
    }

    @Test
    void leavesTheLinkOutWhileTheSiteIsNotConfigured() {
        assertThat(new SiteSettings("", true).eventUrl(ID)).isEmpty();
        assertThat(new SiteSettings(null, false).eventUrl(ID)).isEmpty();
    }

    @Test
    void refusesInvalidUrlsAndPlainHttpInProduction() {
        assertThatThrownBy(() -> new SiteSettings("ticketfy.example", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SiteSettings("ftp://ticketfy.example", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SiteSettings("https://ticketfy.example/?a=1", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SiteSettings("http://ticketfy.example", true)).isInstanceOf(IllegalStateException.class);
        assertThat(new SiteSettings("http://localhost:5173", false).eventUrl(ID)).contains("http://localhost:5173/events/" + ID);
    }
}
