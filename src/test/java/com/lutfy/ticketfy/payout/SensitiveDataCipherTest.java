package com.lutfy.ticketfy.payout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataCipherTest {

    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void developmentProfileUsesTheFixedDevelopmentKey() throws Exception {
        var base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        var dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));

        assertThat(dev.getProperty("ticketfy.payout.encryption-key"))
                .isEqualTo("${ticketfy.payout.development-encryption-key}");
        assertThat(Base64.getDecoder().decode(base.getProperty("ticketfy.payout.development-encryption-key")))
                .hasSize(32);
        assertThat(base.getProperty("ticketfy.payout.encryption-key")).isEqualTo("${PAYOUT_ENCRYPTION_KEY}");
    }

    @Test
    void prodRefusesToStartWithTheDevelopmentKey() throws Exception {
        var developmentKey = developmentKey();

        new ApplicationContextRunner()
                .withBean(SensitiveDataCipher.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "ticketfy.payout.encryption-key=" + developmentKey,
                        "ticketfy.payout.development-encryption-key=" + developmentKey)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("must not use the development key"));
    }

    @Test
    void prodStartsWithItsOwnKey() throws Exception {
        new ApplicationContextRunner()
                .withBean(SensitiveDataCipher.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "ticketfy.payout.encryption-key=" + OTHER_KEY,
                        "ticketfy.payout.development-encryption-key=" + developmentKey())
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SensitiveDataCipher.class));
    }

    @Test
    void developmentKeyIsAcceptedOutsideProd() throws Exception {
        var developmentKey = developmentKey();

        var cipher = new SensitiveDataCipher(developmentKey, developmentKey, new MockEnvironment().withProperty("x", "y"));

        assertThat(cipher.decrypt(cipher.encrypt("52998224725"))).isEqualTo("52998224725");
    }

    @Test
    void encryptsWithRandomIvAndDetectsTampering() {
        var cipher = new SensitiveDataCipher(OTHER_KEY, "unused", new MockEnvironment());

        var first = cipher.encrypt("maria@example.com");
        var second = cipher.encrypt("maria@example.com");

        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("maria");
        assertThat(cipher.decrypt(first)).isEqualTo("maria@example.com");
        var tampered = first.substring(0, first.length() - 2) + (first.endsWith("A=") ? "B=" : "A=");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNot32Bytes() {
        var shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new SensitiveDataCipher(shortKey, "unused", new MockEnvironment()))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new SensitiveDataCipher("not base64!", "unused", new MockEnvironment()))
                .hasMessageContaining("base64");
    }

    private static String developmentKey() throws Exception {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("ticketfy.payout.development-encryption-key");
    }
}
