package com.saroga.timewarp.infrastructure.config;

import com.saroga.timewarp.infrastructure.security.AllowedOrigins;
import com.saroga.timewarp.infrastructure.security.AuthInterceptor;
import com.saroga.timewarp.infrastructure.security.JwtService;
import com.saroga.timewarp.infrastructure.security.LoginAttemptLimiter;
import com.saroga.timewarp.infrastructure.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;

/** Beans de seguridad: tokens, hash de contrasenas, limitadores, rutas REST protegidas y CORS. */
@Configuration
@EnableScheduling
public class SecurityConfig implements WebMvcConfigurer {

    private final JwtService jwtService;
    private final String[] allowedOrigins;
    private final double authBurst, wsBurst, wsPerSecond;

    public SecurityConfig(@Value("${timewarp.security.jwt-secret:}") String secret,
                          @Value("${timewarp.security.token-ttl-minutes:720}") long ttlMinutes,
                          @Value("${timewarp.cors.allowed-origins:}") String origins,
                          @Value("${timewarp.security.auth-burst-per-ip:20}") double authBurst,
                          @Value("${timewarp.security.ws-burst:60}") double wsBurst,
                          @Value("${timewarp.security.ws-per-second:20}") double wsPerSecond) {
        this.authBurst = authBurst;
        this.wsBurst = wsBurst;
        this.wsPerSecond = wsPerSecond;
        this.jwtService = new JwtService(secret, Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
        this.allowedOrigins = AllowedOrigins.from(origins);
    }

    @Bean
    public JwtService jwtService() { return jwtService; }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter() { return new LoginAttemptLimiter(); }

    /** Limite por IP para registro / login / invitado: rafaga de 20 y luego 1 cada 3 segundos. */
    @Bean
    public RateLimiter authRateLimiter() { return new RateLimiter(authBurst, 1.0 / 3.0); }

    /** Limite por sesion WebSocket: rafaga de 60 mensajes y luego 20 por segundo (un humano no pasa de 3-4; un bot de pruebas, con ritmo propio, de unos 15). */
    @Bean
    public RateLimiter wsRateLimiter() { return new RateLimiter(wsBurst, wsPerSecond); }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(jwtService))
                .addPathPatterns("/api/users/me", "/api/menu/decks", "/api/menu/decks/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
