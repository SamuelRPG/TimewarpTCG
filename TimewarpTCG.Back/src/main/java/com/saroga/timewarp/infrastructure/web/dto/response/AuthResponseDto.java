package com.saroga.timewarp.infrastructure.web.dto.response;

public class AuthResponseDto {
    public String id;
    public String username;
    public String token;      // JWT firmado: se envia como "Authorization: Bearer <token>" y en el CONNECT de STOMP
    public boolean guest;

    public AuthResponseDto(String id, String username, String token, boolean guest) {
        this.id = id;
        this.username = username;
        this.token = token;
        this.guest = guest;
    }
}
