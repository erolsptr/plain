package com.planit.controller;

import com.planit.model.User;
import com.planit.model.dto.ProfileDTO;
import com.planit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;

    /**
     * O an giriş yapmış olan kullanıcının profil bilgilerini döndürür.
     */
    @GetMapping
    public ResponseEntity<ProfileDTO> getMyProfile(Authentication authentication) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        
        ProfileDTO profileDTO = new ProfileDTO(user.getName(), user.getEmail(), user.getAvatarId());
        
        return ResponseEntity.ok(profileDTO);
    }

    /**
     * O an giriş yapmış olan kullanıcının avatarını günceller.
     * Request Body'de {"avatarId": "new-avatar-name"} şeklinde bir JSON beklenir.
     */
    @PutMapping("/avatar")
    public ResponseEntity<Void> updateMyAvatar(@RequestBody Map<String, String> payload, Authentication authentication) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        
        String newAvatarId = payload.get("avatarId");
        if (newAvatarId == null || newAvatarId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build(); // Avatar ID'si boş olamaz
        }

        user.setAvatarId(newAvatarId.trim());
        userRepository.save(user);
        
        return ResponseEntity.ok().build();
    }
}