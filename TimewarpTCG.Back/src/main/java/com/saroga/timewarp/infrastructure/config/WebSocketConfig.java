package com.saroga.timewarp.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
            //UNITY escucha lo que el servidor devuelve a traves de /topic
            config.enableSimpleBroker("/topic");
            //UNITY envia al servidor a traves de /app
            config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry config) {
        //url inicial a la que se conecta unity
        config.addEndpoint("/timewarp-ws").setAllowedOrigins("*");
    }
}
