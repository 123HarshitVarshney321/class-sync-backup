package com.classsync.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/test/admin").hasRole("ADMIN")
                        .requestMatchers("/api/test/professor").hasAnyRole("PROFESSOR", "ADMIN")
                        .requestMatchers("/api/test/authenticated").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/complexes/**", "/api/rooms/**", "/api/facilities/**", "/api/timetables/**", "/api/schedule-occurrences/**").hasAnyRole("PROFESSOR", "ADMIN")
                        .requestMatchers("/api/complexes/**", "/api/rooms/**", "/api/facilities/**", "/api/timetables/**", "/api/schedule-occurrences/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.POST, "/api/classes/**").hasAnyRole("PROFESSOR", "ADMIN")
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
