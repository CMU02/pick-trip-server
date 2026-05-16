package travel_agency.pick_trip.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    // 사용자당 하나의 리프레시 토큰만 유지한다 (재로그인 시 덮어쓰기).
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static RefreshToken of(UUID userId, String token, LocalDateTime expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.userId = userId;
        refreshToken.token = token;
        refreshToken.expiresAt = expiresAt;
        return refreshToken;
    }

    public void rotate(String newToken, LocalDateTime newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }
}
