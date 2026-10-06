package com.fastorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String accessToken;

    @Builder.Default
    private String tipo = "Bearer";

    private String rol;
    private String email;
}
