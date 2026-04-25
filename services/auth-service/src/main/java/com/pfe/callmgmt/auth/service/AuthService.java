package com.pfe.callmgmt.auth.service;

import com.pfe.callmgmt.auth.dto.AuthResponse;
import com.pfe.callmgmt.auth.dto.LoginRequest;
import com.pfe.callmgmt.auth.dto.RegisterRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(String refreshToken);
}
