package travel_agency.pick_trip.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.dto.response.OAuthExchangeResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeCodeStore;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeData;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.AuthException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthExchangeCodeStore exchangeCodeStore;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    /**
     * OAuth 일회용 교환 코드를 실제 토큰으로 교환한다.
     *
     * <p>코드는 1회성이며(회수 즉시 소멸), 코드에 바인딩된 nonce 가 요청 nonce 와 일치해야 한다.
     * 코드가 없거나·만료·재사용이거나·nonce 가 불일치하면 {@link ErrorCode#AUTH_INVALID_EXCHANGE_CODE}
     * (401)로 실패한다. 이로써 위조 콜백/재사용 코드/개시 브라우저 불일치를 차단한다.</p>
     */
    public OAuthExchangeResponse exchange(String code, String nonce) {
        OAuthExchangeData data = exchangeCodeStore.consume(code)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_INVALID_EXCHANGE_CODE));

        // 개시 브라우저 바인딩 검증: 코드에 묶인 nonce 와 프론트가 제출한 nonce 가 같아야 한다.
        // nonce 미바인딩(null) 코드는 검증 불가로 간주해 거부한다.
        if (data.nonce() == null || !constantTimeEquals(data.nonce(), nonce)) {
            throw new AuthException(ErrorCode.AUTH_INVALID_EXCHANGE_CODE);
        }

        return new OAuthExchangeResponse(data.accessToken(), data.refreshToken());
    }

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

    // 타이밍 공격을 피하기 위해 nonce 비교를 상수 시간으로 수행한다.
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
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
