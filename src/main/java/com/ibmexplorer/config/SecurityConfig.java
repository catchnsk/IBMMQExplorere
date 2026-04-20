package com.ibmexplorer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibmexplorer.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css",
                                 "/*.ico", "/*.png", "/login", "/manifest.json").permitAll()
                .requestMatchers("/api/mq/health").permitAll()
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/mq/config/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/mq/config/save").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "VIEWER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    var roles = auth.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .toList();
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.success(Map.of("username", auth.getName(), "roles", roles)));
                })
                .failureHandler((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.error("Invalid username or password"));
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> {
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.success(null, "Logged out successfully"));
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.error("Authentication required"));
                })
                .accessDeniedHandler((req, res, accessDeniedEx) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.error("Access denied: insufficient role"));
                })
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin-username}") String adminUser,
            @Value("${app.security.admin-password}") String adminPass,
            @Value("${app.security.viewer-username}") String viewerUser,
            @Value("${app.security.viewer-password}") String viewerPass,
            PasswordEncoder encoder) {

        UserDetails admin = User.builder()
            .username(adminUser)
            .password(encoder.encode(adminPass))
            .roles("ADMIN", "VIEWER")
            .build();
        UserDetails viewer = User.builder()
            .username(viewerUser)
            .password(encoder.encode(viewerPass))
            .roles("VIEWER")
            .build();
        return new InMemoryUserDetailsManager(admin, viewer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
