package com.planit.controller;

import com.planit.model.User;
import com.planit.model.dto.NameChangeRequest; // YENİ IMPORT
import com.planit.model.dto.PasswordChangeRequest; // YENİ IMPORT
import com.planit.model.dto.ProfileDTO;
import com.planit.repository.UserRepository;
import com.planit.service.ProfileService; // YENİ IMPORT
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
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
    private final ProfileService profileService; // YENİ SERVİS

    @GetMapping
    public ResponseEntity<ProfileDTO> getMyProfile(Authentication authentication) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        
        ProfileDTO profileDTO = new ProfileDTO(user.getName(), user.getEmail(), user.getAvatarId());
        
        return ResponseEntity.ok(profileDTO);
    }

    @PutMapping("/avatar")
    public ResponseEntity<Void> updateMyAvatar(@RequestBody Map<String, String> payload, Authentication authentication) {
        String newAvatarId = payload.get("avatarId");
        profileService.updateAvatar(authentication.getName(), newAvatarId);
        return ResponseEntity.ok().build();
    }

    // --- YENİ ENDPOINT'LER ---

    /**
     * O an giriş yapmış olan kullanıcının görünen adını günceller.
     */
    @PutMapping("/name")
public ResponseEntity<Void> updateMyName(@RequestBody NameChangeRequest request, Authentication authentication) {
    try {
        profileService.updateName(authentication.getName(), request.getNewName());
        return ResponseEntity.ok().build();
    } catch (IllegalStateException e) {
        // Eğer isim zaten alınmışsa, 409 Conflict (Çakışma) durum kodu döndür
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    } catch (IllegalArgumentException e) {
        // Eğer isim geçerli değilse (çok kısaysa vb.) 400 Bad Request döndür
        return ResponseEntity.badRequest().build();
    }
}

    /**
     * O an giriş yapmış olan kullanıcının şifresini günceller.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> updateMyPassword(@RequestBody PasswordChangeRequest request, Authentication authentication) {
        try {
            profileService.updatePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            // Eğer mevcut şifre yanlışsa 400 Bad Request döndür
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * O an giriş yapmış olan kullanıcının hesabını kalıcı olarak siler.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        profileService.deleteAccount(authentication.getName());
        return ResponseEntity.ok().build();
    }
}