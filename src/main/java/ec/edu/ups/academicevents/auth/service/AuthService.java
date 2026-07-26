package ec.edu.ups.academicevents.auth.service;

import ec.edu.ups.academicevents.auth.dto.AuthUserResponse;
import ec.edu.ups.academicevents.auth.dto.LoginRequest;
import ec.edu.ups.academicevents.auth.dto.RefreshRequest;
import ec.edu.ups.academicevents.auth.dto.RegisterRequest;
import ec.edu.ups.academicevents.auth.dto.TokenResponse;

public interface AuthService {

    AuthUserResponse register(RegisterRequest request, String ip);

    TokenResponse login(LoginRequest request, String ip);

    TokenResponse refresh(RefreshRequest request, String ip);

    void logout(String refreshToken);

    AuthUserResponse me();
}
