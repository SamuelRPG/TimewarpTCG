package com.saroga.timewarp.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/** Exige "Authorization: Bearer <token>" en las rutas REST protegidas y deja al usuario autenticado en la peticion. */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "timewarp.auth";
    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) { this.jwt = jwt; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;   // preflight de CORS
        try {
            String token = JwtService.bearer(request.getHeader("Authorization"));
            request.setAttribute(ATTR, jwt.parse(token));
            return true;
        } catch (InvalidTokenException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8));
            return false;
        }
    }

    /** El usuario autenticado de esta peticion (el interceptor garantiza que existe en las rutas protegidas). */
    public static AuthPrincipal require(HttpServletRequest request) {
        Object o = request.getAttribute(ATTR);
        if (!(o instanceof AuthPrincipal)) throw new InvalidTokenException("No autenticado.");
        return (AuthPrincipal) o;
    }
}
