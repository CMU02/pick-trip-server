package travel_agency.pick_trip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * OAuth 일회용 교환 코드 → 토큰 교환 요청.
 *
 * @param code  성공 콜백에서 받은 opaque 교환 코드
 * @param nonce 로그인 시작 시 개시 브라우저가 생성·보관한 nonce. 코드에 바인딩된 값과 일치해야 한다.
 */
public record OAuthExchangeRequest(
        @NotBlank String code,
        @NotBlank String nonce
) {}
