package travel_agency.pick_trip.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.AuthException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    @Transactional
    public TokenRefreshResponse refresh(String refreshToken) {
        parseRefreshToken(refreshToken);

        // DB에 저장된 토큰과 일치하는지 검증한다. 로그아웃 후 재사용을 방지한다.
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND));

        UUID uid = stored.getUserId();
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_INVALID_TOKEN));

        // 탈퇴한 사용자는 토큰 재발급을 허용하지 않는다.
        if (user.isDeleted()) {
            throw new AuthException(ErrorCode.AUTH_FORBIDDEN);
        }

        // DB에서 최신 사용자 정보를 읽어 액세스 토큰에 반영한다 (권한 변경 즉시 적용).
        JwtUserInfo jwtUserInfo = new JwtUserInfo(
                user.getUid(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name()
        );

        // 리프레시 토큰도 함께 교체해 탈취된 토큰의 재사용을 방지한다 (Refresh Token Rotation).
        String newRefreshToken = jwtUtil.generateRefreshToken(jwtUserInfo);
        stored.rotate(newRefreshToken, LocalDateTime.now().plusDays(refreshTokenExpireTimeDays));

        return new TokenRefreshResponse(jwtUtil.generateAccessToken(jwtUserInfo), newRefreshToken);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteById(userId);
    }

    private void parseRefreshToken(String refreshToken) {
        try {
            jwtUtil.parseToken(refreshToken).getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("리프레시 토큰 검증 실패 - message: {}", e.getMessage());
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
