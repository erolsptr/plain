package com.planit.model.dto;

// sadece e-posta ve şifre taşımak için kullanılan basit bir DTO
public record LoginRequest(String email, String password) {
}