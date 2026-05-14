package travel_agency.pick_trip.gloal.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import travel_agency.pick_trip.gloal.filter.JwtFilter;
import travel_agency.pick_trip.gloal.filter.TraceIdFilter;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final TraceIdFilter traceIdFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth - 인증 불필요
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/kakao").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/refresh").permitAll()
                        // Content - 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents/**").permitAll()
                        // Share - 공유 토큰 조회 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/share/**").permitAll()
                        // 나머지 모든 요청은 인증 필요
                        // TODO: 현재 개발 중이기 때문에 잠시 요청은 인증 불필요, 추후 authenticated() 설정
                        .anyRequest().permitAll()

                )
                .addFilterBefore(traceIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
