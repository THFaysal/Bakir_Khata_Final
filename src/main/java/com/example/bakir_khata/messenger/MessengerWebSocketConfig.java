package com.example.bakir_khata.messenger;

import com.example.bakir_khata.security.jwt.JwtSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.*;
import java.util.List;

@Configuration @EnableWebSocketMessageBroker @RequiredArgsConstructor
public class MessengerWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder jwtDecoder;
    private final JwtSecurity jwtSecurity;
    @Value("${app.payments.public-base-url:http://localhost:8080}") private String publicBaseUrl;

    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Do not use wildcard origins for an authenticated WebSocket endpoint.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(publicBaseUrl);
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor=org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor==null) return message;
                StompCommand command=accessor.getCommand();
                if (StompCommand.CONNECT.equals(command)) {
                    List<String> headers=accessor.getNativeHeader("Authorization");
                    if (headers!=null && !headers.isEmpty() && headers.get(0).startsWith("Bearer ")) {
                        accessor.setUser(jwtSecurity.convert(jwtDecoder.decode(headers.get(0).substring(7))));
                    }
                    if (accessor.getUser()==null) throw new MessagingException("WebSocket login required.");
                } else if (StompCommand.SUBSCRIBE.equals(command)) {
                    if (!"/user/queue/messages".equals(accessor.getDestination()))
                        throw new MessagingException("Subscription not allowed.");
                } else if (StompCommand.SEND.equals(command)) {
                    if (!"/app/messages.send".equals(accessor.getDestination()))
                        throw new MessagingException("Destination not allowed.");
                }
                return message;
            }
        });
    }
}
