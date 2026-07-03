package com.aiot.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置。
 * <p>
 * 配置 JWT 无状态认证、CORS 跨域策略和 API 访问控制规则。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §5、docs/ood_infrastructure.md §3.6
 * </p>
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public WebSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                             RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证端点——无需认证
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                // WebSocket 握手——由 WebSocket Handler 自行处理认证
                .requestMatchers("/ws/**").permitAll()
                // S5 救援端点——仅 RESCUE 角色
                .requestMatchers("/api/v1/emergency/**").hasRole("RESCUE")
                // S4 车队管理端点——仅 MANAGER 角色
                .requestMatchers("/api/v1/fleet/**").hasRole("MANAGER")
                // S3 家属监护端点——FAMILY 角色
                .requestMatchers("/api/v1/guardianship/**").hasRole("FAMILY")
                // 家属车辆查询——FAMILY 角色
                .requestMatchers("/api/v1/vehicles/**").hasRole("FAMILY")
                // SparkRTC Token——FAMILY 角色
                .requestMatchers("/api/v1/sparkrtc/**").hasRole("FAMILY")
                // S6 OTA 管理端点——MANAGER 角色
                .requestMatchers("/api/v1/ota/**").hasRole("MANAGER")
                // S1/S2 查询端点——已认证用户
                .requestMatchers(HttpMethod.GET, "/api/v1/drivers/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/trips/**").authenticated()
                // 其余端点——已认证
                .requestMatchers("/api/v1/**").authenticated()
                // 静态资源——放行
                .anyRequest().permitAll()
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
