package com.planit.service;

import com.planit.model.User;
import com.planit.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
}