package travel_agency.pick_trip.domain.auth.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.client.KakaoApiClient;
import travel_agency.pick_trip.domain.auth.client.KakaoAuthClient;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoTokenResponse;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoUserInfoResponse;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final KakaoApiClient kakaoApiClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    @Transactional
    public LoginResponse login(String authorizationCode) {
        KakaoTokenResponse kakaoToken = fetchKakaoToken(authorizationCode);
        KakaoUserInfoResponse userInfo = fetchKakaoUserInfo(kakaoToken.accessToken());

        // 재로그인 시 닉네임·프로필 이미지를 최신 카카오 정보로 동기화한다.
        // 이메일은 최초 가입 시만 저장하고 이후 변경하지 않는다 (동의 항목 변경 방어).
        User user = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, userInfo.providerUserId())
                .map(existing -> {
                    existing.updateProfile(userInfo.nickname(), userInfo.profileImageUrl());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(OAuthProvider.KAKAO)
                        .providerUserId(userInfo.providerUserId())
                        .email(userInfo.email())
                        .nickname(userInfo.nickname())
                        .profileImageUrl(userInfo.profileImageUrl())
                        .build()));

        JwtUserInfo jwtUserInfo = new JwtUserInfo(
                user.getUid(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(jwtUserInfo);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpireTimeDays);

        // 재로그인 시 기존 리프레시 토큰을 갱신하고, 최초 로그인 시 새로 저장한다.
        refreshTokenRepository.findById(user.getUid())
                .ifPresentOrElse(
                        existing -> existing.rotate(newRefreshToken, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.of(user.getUid(), newRefreshToken, expiresAt))
                );

        return new LoginResponse(jwtUtil.generateAccessToken(jwtUserInfo), newRefreshToken);
    }

    private KakaoTokenResponse fetchKakaoToken(String authorizationCode) {
        try {
            return kakaoAuthClient.getToken("authorization_code", clientId, redirectUri, authorizationCode);
        } catch (FeignException e) {
            // 인가코드 만료, 이미 사용된 코드, 잘못된 redirect_uri 등이 원인일 수 있다.
            // 카카오 원문 메시지는 보안상 클라이언트에 노출하지 않고 로그에만 남긴다.
            log.error("카카오 토큰 발급 실패 - status: {}, message: {}", e.status(), e.getMessage());
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }
    }

    private KakaoUserInfoResponse fetchKakaoUserInfo(String accessToken) {
        try {
            return kakaoApiClient.getUserInfo("Bearer " + accessToken);
        } catch (FeignException e) {
            log.error("카카오 사용자 정보 조회 실패 - status: {}, message: {}", e.status(), e.getMessage());
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }
    }
}
