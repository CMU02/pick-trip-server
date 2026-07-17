package travel_agency.pick_trip.domain.auth.dto.response;

/**
 * OAuth 교환 응답. 재발급 응답과 동일하게 액세스/리프레시 토큰 쌍을 본문으로 돌려준다.
 */
public record OAuthExchangeResponse(
        String accessToken,
        String refreshToken
) {}
