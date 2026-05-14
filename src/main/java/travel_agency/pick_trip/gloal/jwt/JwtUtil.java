package travel_agency.pick_trip.gloal.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.user.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret-key}")
    private String secretKey;

    // 밀리초(ms) 단위. 예: 1800000 = 30분
    @Value("${jwt.access-token-expire-time-ms}")
    private Long accessTokenExpireTimeMs;

    // 일(day) 단위. 예: 14 = 14일
    @Value("${jwt.refresh-token-expire-time-days}")
    private Long refreshTokenExpireTimeDays;

    public String generateAccessToken(User user) {
        return generateToken(user, TokenType.ACCESS);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TokenType.REFRESH);
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token);
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JWT 토큰 생성 공통 메서드
     * @param user user 대상 사용자 엔티티
     * @param type 발급할 토큰 종류 (ACCESS, REFRESH)
     * @return 생성된 JWT 문자열
     */
    private String generateToken(User user, TokenType type) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(user.getUid().toString())
                .issuedAt(Date.from(now));

        if (type.equals(TokenType.ACCESS)) {
            builder.claim("name", user.getNickname())
                    .claim("email", user.getEmail())
                    .claim("role", user.getRole().name())
                    .expiration(
                            Date.from(now.plus(Duration.ofMillis(accessTokenExpireTimeMs)))
                    );
        } else {
            builder.expiration(
                    Date.from(now.plus(Duration.ofDays(refreshTokenExpireTimeDays)))
            );
        }

        return builder.signWith(getSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    enum TokenType {
        ACCESS, REFRESH
    }
}
