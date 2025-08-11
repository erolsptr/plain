package com.planit.service;

import com.planit.model.PokerRoom;
import com.planit.model.RoomDetails;
import com.planit.model.User;
import com.planit.repository.PokerRoomRepository;
import com.planit.repository.RoomDetailsRepository;
import com.planit.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomDetailsService {

    private final RoomDetailsRepository roomDetailsRepository;
    private final PokerRoomRepository pokerRoomRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomDetails saveRoomDetails(RoomDetails details, String requesterEmail) {
        String roomId = details.getRoomId();
        
        // 1. Yetki Kontrolü: Bu işlemi yapmaya çalışan kişi, odanın sahibi mi?
        // Bu, başkasının odasının adını değiştirmesini engeller.
        PokerRoom room = pokerRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Detayları kaydedilecek oda bulunamadı: " + roomId));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("İsteği yapan kullanıcı bulunamadı: " + requesterEmail));

        if (!room.getOwner().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Sadece oda sahibi oda detaylarını değiştirebilir.");
        }

        // 2. Yeni bir kayıt mı, yoksa güncelleme mi olduğunu kontrol et
        return roomDetailsRepository.findById(roomId)
                .map(existingDetails -> {
                    // Güncelleme: Var olan kaydın sadece ismini ve son aktivite tarihini güncelle
                    existingDetails.setRoomName(details.getRoomName());
                    existingDetails.setLastActivityDate(LocalDateTime.now());
                    return roomDetailsRepository.save(existingDetails);
                })
                .orElseGet(() -> {
                    // Yeni Kayıt: Oluşturulma ve son aktivite tarihlerini ayarla
                    details.setCreationDate(LocalDateTime.now());
                    details.setLastActivityDate(LocalDateTime.now());
                    return roomDetailsRepository.save(details);
                });
    }

    public List<RoomDetails> findRoomDetailsByIds(Set<String> roomIds) {
        // Bu metot, yetki kontrolü gerektirmez çünkü sadece ID'si bilinen odaların
        // herkese açık olabilecek detaylarını (isim, tarih) getirir.
        // Hassas bilgi içermez.
        return roomDetailsRepository.findByRoomIdIn(roomIds);
    }
}
