package com.planit.config;

import com.planit.service.JwtService; // YENİ IMPORT
import lombok.RequiredArgsConstructor; // YENİ IMPORT
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered; // YENİ IMPORT
import org.springframework.core.annotation.Order; // YENİ IMPORT
import org.springframework.messaging.Message; // YENİ IMPORT
import org.springframework.messaging.MessageChannel; // YENİ IMPORT
import org.springframework.messaging.simp.config.ChannelRegistration; // YENİ IMPORT
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand; // YENİ IMPORT
import org.springframework.messaging.simp.stomp.StompHeaderAccessor; // YENİ IMPORT
import org.springframework.messaging.support.ChannelInterceptor; // YENİ IMPORT
import org.springframework.messaging.support.MessageHeaderAccessor; // YENİ IMPORT
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // YENİ IMPORT
import org.springframework.security.core.userdetails.UserDetails; // YENİ IMPORT
import org.springframework.security.core.userdetails.UserDetailsService; // YENİ IMPORT
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration 
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // Bu yapılandırmanın diğerlerinden önce çalışmasını sağlar
@RequiredArgsConstructor // Final alanlar için constructor oluşturur
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-poker").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Sadece bağlantı (CONNECT) anında kimlik doğrulaması yap
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // "Authorization" başlığını al (React'tan gönderdiğimiz)
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String jwt = authHeader.substring(7);
                        String userEmail = jwtService.extractUsername(jwt);

                        if (userEmail != null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
                            if (jwtService.isTokenValid(jwt, userDetails)) {
                                // Geçerli bir token varsa, Spring Security için bir kimlik doğrulama nesnesi oluştur
                                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                                // Bu kimlik doğrulama nesnesini WebSocket oturumuna ata
                                accessor.setUser(authToken);
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}