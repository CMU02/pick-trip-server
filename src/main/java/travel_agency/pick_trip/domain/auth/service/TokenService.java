package travel_agency.pick_trip.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.AuthException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);

        UUID uid = UUID.fromString(claims.getSubject());
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

        return new TokenRefreshResponse(jwtUtil.generateAccessToken(jwtUserInfo));
    }

    private Claims parseRefreshToken(String refreshToken) {
        try {
            return jwtUtil.parseToken(refreshToken).getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("리프레시 토큰 검증 실패 - message: {}", e.getMessage());
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
