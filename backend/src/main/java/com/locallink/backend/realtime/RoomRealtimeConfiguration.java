package com.locallink.backend.realtime;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.locallink.backend.session.SessionTokenService;

@Configuration
@EnableWebSocketMessageBroker
public class RoomRealtimeConfiguration
        implements WebSocketMessageBrokerConfigurer {

    private final RoomStompAuthorizationInterceptor authorizationInterceptor;
    private final TaskScheduler heartbeatScheduler;

    @Value("${locallink.cors.allowed-origin-patterns:http://localhost:5500,http://127.0.0.1:5500,http://192.168.*.*:*,http://10.*.*.*:*,http://172.*.*.*:*,https://localhost:5500,https://127.0.0.1:5500,https://192.168.*.*:*,https://10.*.*.*:*,https://172.*.*.*:*}")
    private String allowedOriginPatterns;

    public RoomRealtimeConfiguration(
            SessionTokenService sessionTokenService,
            @Qualifier("roomWebSocketHeartbeatScheduler")
            TaskScheduler heartbeatScheduler) {
        this.authorizationInterceptor =
                new RoomStompAuthorizationInterceptor(sessionTokenService);
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        parseOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorizationInterceptor);
    }

    private List<String> parseOriginPatterns() {
        return Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }
}
