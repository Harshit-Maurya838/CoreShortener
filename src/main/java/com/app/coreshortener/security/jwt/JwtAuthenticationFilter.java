package com.app.coreshortener.security.jwt;

import com.app.coreshortener.security.domain.TenantContext;
import com.app.coreshortener.security.domain.UserAdapter;
import com.app.coreshortener.security.services.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = req.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(req, res);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtService.isAccessToken(jwt)) {
                String userEmail = jwtService.extractUsername(jwt);
                UUID tenantIdFromToken = jwtService.extractTenantId(jwt);

                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // Securely bind tenantId from authenticated token or UserDetails
                        if (tenantIdFromToken != null) {
                            TenantContext.setTenantId(tenantIdFromToken);
                        } else if (userDetails instanceof UserAdapter ua) {
                            TenantContext.setTenantId(ua.getTenantId());
                        }
                    }
                }
            }
            filterChain.doFilter(req, res);
        } catch (JwtException | UsernameNotFoundException ex) {
            logger.warn("JWT authentication failed: " + ex.getMessage());
            filterChain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
