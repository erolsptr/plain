package com.planit.service;
import com.planit.model.User;

public record AuthResponse(String token, User user) {
}