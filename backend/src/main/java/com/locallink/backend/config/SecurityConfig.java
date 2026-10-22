package com.locallink.backend.config;

import com.locallink.backend.session.SessionTokenFilter;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Value("${locallink.cors.allowed-origin-patterns:http://localhost:5500,http://127.0.0.1:5500,http://192.168.*.*:*,http://10.*.*.*:*,http://172.*.*.*:*,https://localhost:5500,https://127.0.0.1:5500,https://192.168.*.*:*,https://10.*.*.*:*,https://172.*.*.*:*}")
    private String allowedOriginPatterns;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(Arrays.stream(
                        allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList());

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Content-Type", "Authorization", "X-Upload-Id"
        ));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }


    private final SessionTokenFilter sessionTokenFilter;

    public SecurityConfig(SessionTokenFilter sessionTokenFilter) {
        this.sessionTokenFilter = sessionTokenFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(
                        sessionTokenFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms/join").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms/recover").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms/recover-pin").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\":\"SESSION_TOKEN_REQUIRED\","
                                            + "\"message\":\"A valid session token is required.\"}");
                        })
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());


        return http.build();
    }

}
