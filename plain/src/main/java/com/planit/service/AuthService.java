package com.planit.service;

import com.planit.model.User;
import com.planit.model.dto.LoginRequest;
import com.planit.model.dto.RegisterRequest; 
import com.planit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    public User registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return null; 
        }

        
        User newUser = new User();
        newUser.setName(request.name());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));

        return userRepository.save(newUser);
    }

    public AuthResponse loginUser(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        var user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı kimlik doğrulamadan geçti ama bulunamadı."));
        
        String jwtToken = jwtService.generateToken(user.getEmail());
        
        user.setPassword(null);
        return new AuthResponse(jwtToken, user);
    }
}