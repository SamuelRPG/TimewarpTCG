package com.saroga.timewarp.infrastructure.web.dto.request;

/** Registro / login. La contrasena viaja en el CUERPO (nunca en la URL: las URL acaban en logs). */
public class AuthRequestDto {
    public String username;
    public String password;
}
