package com.lutfy.ticketfy.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSettingsTest {

    private static final String SECRET = "a-proxy-secret-with-at-least-32-characters";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AuthSettings.class)
            .withPropertyValues(
                    "ticketfy.auth.access-token-minutes=10",
                    "ticketfy.auth.refresh-token-idle-minutes=30",
                    "ticketfy.auth.session-max-hours=12",
                    "ticketfy.auth.cookie-name=__Host-ticketfy_rt",
                    "ticketfy.auth.cookie-secure=true",
                    "ticketfy.auth.proxy-secret=" + SECRET);

    @Test
    void prodRefusesToStartWithAnInsecureCookie() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("ticketfy.auth.cookie-name=ticketfy_rt", "ticketfy.auth.cookie-secure=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("cookie-secure must be true in the prod profile"));
    }

    @Test
    void prodRefusesToStartWithoutTheProxySecret() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("ticketfy.auth.proxy-secret=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PROXY_SHARED_SECRET is required in the prod profile"));
    }

    @Test
    void prodStartsWithASecureCookieAndTheProxySecret() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(AuthSettings.class));
    }

    @Test
    void developmentStartsWithoutTheProxySecretAndAnInsecureCookie() {
        runner.withPropertyValues("ticketfy.auth.proxy-secret=", "ticketfy.auth.cookie-name=ticketfy_rt",
                        "ticketfy.auth.cookie-secure=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void hostPrefixedCookieRequiresSecure() {
        runner.withPropertyValues("ticketfy.auth.cookie-secure=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("__Host- prefix requires"));
    }

    @Test
    void shortProxySecretIsRejected() {
        runner.withPropertyValues("ticketfy.auth.proxy-secret=too-short")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("at least 32 characters"));
    }

    @Test
    void propertiesDefaultToTheProductionCookieAndRelaxItOnlyInDevelopment() throws Exception {
        var base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        var dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));
        var prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

        assertThat(base.getProperty("ticketfy.auth.cookie-name")).isEqualTo("__Host-ticketfy_rt");
        assertThat(base.getProperty("ticketfy.auth.cookie-secure")).isEqualTo("true");
        assertThat(base.getProperty("ticketfy.auth.proxy-secret")).isEqualTo("${PROXY_SHARED_SECRET:}");
        assertThat(base.getProperty("ticketfy.auth.access-token-minutes")).isEqualTo("10");
        assertThat(base.getProperty("ticketfy.auth.refresh-token-idle-minutes")).isEqualTo("30");
        assertThat(base.getProperty("ticketfy.auth.session-max-hours")).isEqualTo("12");
        assertThat(dev.getProperty("ticketfy.auth.cookie-secure")).isEqualTo("false");
        assertThat(prod.getProperty("ticketfy.auth.cookie-secure")).isNull();
        assertThat(prod.getProperty("ticketfy.auth.proxy-secret")).isNull();
    }
}
