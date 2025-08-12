package com.planit.controller;

import com.planit.model.User;
import com.planit.model.dto.NameChangeRequest;
import com.planit.model.dto.PasswordChangeRequest;
import com.planit.model.dto.ProfileDTO;
import com.planit.repository.UserRepository;
import com.planit.service.ProfileService;
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
    private final ProfileService profileService;

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

    @PutMapping("/name")
    public ResponseEntity<Void> updateMyName(@RequestBody NameChangeRequest request, Authentication authentication) {
        try {
            profileService.updateName(authentication.getName(), request.getNewName());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/password")
    public ResponseEntity<Void> updateMyPassword(@RequestBody PasswordChangeRequest request, Authentication authentication) {
        try {
            profileService.updatePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}