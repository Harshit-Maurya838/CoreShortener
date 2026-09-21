package com.app.coreshortener.security.services;

import com.app.coreshortener.Models.Role;
import com.app.coreshortener.Models.User;
import com.app.coreshortener.Repository.UserRepository;
import com.app.coreshortener.auth.AuthenticationRequest;
import com.app.coreshortener.auth.AuthenticationResponse;
import com.app.coreshortener.auth.RegisterRequest;
import com.app.coreshortener.security.domain.UserAdapter;
import com.app.coreshortener.security.jwt.JwtService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;

    public AuthenticationResponse register(RegisterRequest req){
        if(userRepository.existsByEmail(req.email())){
            throw new IllegalArgumentException("Email Already exists");
        }

        User user = User.builder()
                .tenantId(req.tenantId())
                .email(req.email())
                .password_hash(passwordEncoder.encode(req.password()))
                .role(Role.MEMBER)
                .build();

        userRepository.save(user);
        String jwtToken = jwtService.generateToken(new UserAdapter(user));
        return new AuthenticationResponse(jwtToken);
    }

    public AuthenticationResponse authenticate(AuthenticationRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        String jwtToken = jwtService.generateToken(new UserAdapter(user));
        return new AuthenticationResponse(jwtToken);
    }
}
