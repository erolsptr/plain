package com.planit.service;

import com.planit.model.PokerRoom; // YENİ IMPORT
import com.planit.model.User;
import com.planit.repository.PokerRoomRepository; // YENİ IMPORT
import com.planit.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy; // YENİ IMPORT
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set; // YENİ IMPORT
import java.util.stream.Collectors; // YENİ IMPORT

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PokerRoomRepository pokerRoomRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Circular dependency'yi (döngüsel bağımlılık) çözmek için @Lazy anotasyonu
    private final RoomService roomService;

    // Helper method to find user by email
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Transactional
    public void updateAvatar(String userEmail, String newAvatarId) {
        if (newAvatarId == null || newAvatarId.trim().isEmpty()) {
            throw new IllegalArgumentException("Avatar ID cannot be empty.");
        }
        User user = getUserByEmail(userEmail);
        user.setAvatarId(newAvatarId.trim());
        userRepository.save(user);
    }

    @Transactional
    public void updateName(String userEmail, String newName) {
        if (newName == null || newName.trim().isEmpty() || newName.length() < 3) {
            throw new IllegalArgumentException("Name is not valid.");
        }
        User user = getUserByEmail(userEmail);

        if (userRepository.findByName(newName.trim()).isPresent() && !user.getName().equals(newName.trim())) {
             throw new IllegalStateException("This name is already taken.");
        }

        user.setName(newName.trim());
        userRepository.save(user);
    }

    @Transactional
    public void updatePassword(String userEmail, String currentPassword, String newPassword) {
        User user = getUserByEmail(userEmail);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(String userEmail) {
        User userToDelete = getUserByEmail(userEmail);

        // 1. Kullanıcının sahip olduğu odaların bir kopyasını al (döngüde değişiklik yaparken sorun yaşamamak için)
        Set<String> ownedRoomIds = userToDelete.getOwnedRooms().stream()
                                    .map(PokerRoom::getId)
                                    .collect(Collectors.toSet());

        // 2. Sahip olduğu her bir odayı, RoomService'in kendi silme metodunu kullanarak sil.
        // Bu, odaya bağlı tüm katılımcıları, görevleri ve oyları da temizler.
        ownedRoomIds.forEach(roomId -> roomService.deleteRoom(roomId, userEmail));

        // 3. Artık kullanıcıyla ilişkili hiçbir oda kalmadığına göre, kullanıcıyı güvenle sil.
        userRepository.delete(userToDelete);
    }
}