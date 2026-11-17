package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.payment.pix.internal.InternalPaymentAuthFilter;
import com.lutfy.ticketfy.infra.logging.RequestIdFilter;
import com.lutfy.ticketfy.infra.security.AuthOriginFilter;
import com.lutfy.ticketfy.infra.security.JsonAccessDeniedHandler;
import com.lutfy.ticketfy.infra.security.JsonAuthenticationEntryPoint;
import com.lutfy.ticketfy.infra.security.SecurityFilter;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final InternalPaymentAuthFilter internalPaymentAuthFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            SecurityFilter securityFilter,
            InternalPaymentAuthFilter internalPaymentAuthFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) {
        this.securityFilter = securityFilter;
        this.internalPaymentAuthFilter = internalPaymentAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // ==========================================
    // SECURITY FILTER CHAIN
    // ==========================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http

                // CSRF
                .csrf(csrf -> csrf.disable())

                // CORS
                .cors(Customizer.withDefaults())

                // Stateless JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Tratamento de erros
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // ==========================================
                // AUTORIZAÇÃO
                // ==========================================

                .authorizeHttpRequests(auth -> auth

                        // Tratamento de erros
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // ==================================
                        // USUÁRIOS
                        // ==================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/users"
                        ).permitAll()

                        // ==================================
                        // EVENTOS
                        // ==================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/events/mine"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/events",
                                "/events/*"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/events/*/calendar.ics"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/events/*/ticket-types"
                        ).permitAll()

                        // ==================================
                        // AUTENTICAÇÃO
                        // ==================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/login",
                                "/auth/google",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/password-reset/request",
                                "/auth/password-reset/confirm"
                        ).permitAll()

                        // ==================================
                        // PAGAMENTOS INTERNOS GO → JAVA
                        // ==================================

                        /*
                         * Esta rota não exige JWT de usuário.
                         *
                         * A autenticação entre serviços é
                         * realizada pelo InternalPaymentAuthFilter.
                         *
                         * O filtro valida o token interno
                         * enviado pelo microserviço Go.
                         */

                        .requestMatchers(
                                HttpMethod.POST,
                                "/internal/payments/confirm"
                        ).permitAll()

                        // ==================================
                        // DOCUMENTAÇÃO
                        // ==================================

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ==================================
                        // HEALTH CHECK
                        // ==================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health"
                        ).permitAll()

                        // ==================================
                        // DEMAIS ROTAS
                        // ==================================

                        .anyRequest().authenticated()
                )

                // ==========================================
                // FILTRO DE AUTENTICAÇÃO INTERNA
                // ==========================================

                .addFilterBefore(
                        internalPaymentAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                // ==========================================
                // FILTRO JWT
                // ==========================================

                .addFilterBefore(
                        securityFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    // ==========================================
    // EVITAR REGISTRO DUPLO DO FILTRO INTERNO
    // ==========================================

    @Bean
    public FilterRegistrationBean<InternalPaymentAuthFilter>
    internalPaymentAuthFilterRegistration(
            InternalPaymentAuthFilter filter
    ) {

        FilterRegistrationBean<InternalPaymentAuthFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }

    // ==========================================
    // CORS
    // ==========================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${ticketfy.cors.allowed-origins}")
            List<String> allowedOrigins
    ) {

        var config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);

        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Idempotency-Key",
                        AuthOriginFilter.HEADER
                )
        );

        config.setExposedHeaders(
                List.of(
                        "Retry-After",
                        RequestIdFilter.HEADER,
                        HttpHeaders.CONTENT_DISPOSITION
                )
        );

        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                config
        );

        return source;
    }

    // ==========================================
    // PASSWORD ENCODER
    // ==========================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    // ==========================================
    // AUTHENTICATION MANAGER
    // ==========================================

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig
    ) throws Exception {

        return authConfig.getAuthenticationManager();
    }
}