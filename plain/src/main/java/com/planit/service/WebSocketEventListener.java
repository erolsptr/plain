package com.planit.service;

import com.planit.controller.PokerController; // YENİ IMPORT
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final RoomService roomService;
    private final PokerController pokerController; // YENİ: Controller'ı enjekte et

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");

        if (username != null && roomId != null) {
            log.info("Kullanıcı bağlantısı kesildi: {}, Oda: {}", username, roomId);

            // RoomService'e kullanıcının odadan ayrıldığını bildir
            roomService.removeUserFromRoom(roomId, username);

            // Controller üzerinden tüm odaya güncel durumu yayınla
            // Bu, kod tekrarını önler ve tüm yayın mantığını tek bir yerde tutar.
            pokerController.publishFullRoomState(roomId);
        }
    }
}