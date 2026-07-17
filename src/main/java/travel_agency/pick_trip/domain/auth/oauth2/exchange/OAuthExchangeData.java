package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import java.util.UUID;

/**
 * 일회용 교환 코드에 바인딩되는 로그인 결과 데이터.
 *
 * <p>OAuth 성공 시 URL 쿼리로 토큰을 노출하는 대신, 이 데이터를 서버 측 저장소에 담고
 * opaque code 만 프론트로 전달한다. 프론트가 {@code POST /api/v1/auth/oauth/exchange} 로
 * code 를 제출하면 이 데이터를 1회 교환해 실제 토큰을 받는다.</p>
 *
 * @param userId       로그인한 사용자 식별자
 * @param accessToken  발급된 액세스 토큰
 * @param refreshToken 발급된(회전된) 리프레시 토큰
 * @param nonce        개시 브라우저 바인딩용 nonce. 교환 시 프론트가 제출한 값과 일치해야 한다.
 *                     로그인 시작 시 nonce 를 전달하지 않았으면 null 이며, 이 경우 교환은 거부된다.
 */
public record OAuthExchangeData(
        UUID userId,
        String accessToken,
        String refreshToken,
        String nonce
) {}
