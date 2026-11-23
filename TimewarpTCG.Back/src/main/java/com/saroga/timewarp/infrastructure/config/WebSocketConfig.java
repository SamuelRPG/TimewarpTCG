package com.saroga.timewarp.infrastructure.config;

import com.saroga.timewarp.infrastructure.security.AllowedOrigins;
import com.saroga.timewarp.infrastructure.security.JwtService;
import com.saroga.timewarp.infrastructure.security.RateLimiter;
import com.saroga.timewarp.infrastructure.security.StompAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final RateLimiter wsRateLimiter;
    private final String[] allowedOrigins;

    public WebSocketConfig(JwtService jwtService, RateLimiter wsRateLimiter,
                           @Value("${timewarp.cors.allowed-origins:}") String origins) {
        this.jwtService = jwtService;
        this.wsRateLimiter = wsRateLimiter;
        this.allowedOrigins = AllowedOrigins.from(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
            //UNITY escucha lo que el servidor devuelve a traves de /topic
            config.enableSimpleBroker("/topic");
            //UNITY envia al servidor a traves de /app
            config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry config) {
        //url inicial a la que se conecta unity. Un cliente de escritorio no envia Origin; los navegadores solo desde origenes permitidos
        config.addEndpoint("/timewarp-ws").setAllowedOriginPatterns(allowedOrigins);
    }

    /** Todo mensaje que llega pasa por aqui: autenticacion en CONNECT, solo tu canal en SUBSCRIBE, limite de ritmo en SEND. */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthInterceptor(jwtService, wsRateLimiter));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024);        // un mensaje legitimo pesa unos cientos de bytes
        registry.setSendBufferSizeLimit(512 * 1024);
        registry.setSendTimeLimit(20_000);
    }
}
