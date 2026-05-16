package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoTokenResponse;

@FeignClient(name = "kakao-auth", url = "https://kauth.kakao.com")
public interface KakaoAuthClient {

    // 카카오 토큰 API는 JSON이 아닌 application/x-www-form-urlencoded만 허용한다.
    // @RequestParam을 사용하면 OpenFeign이 자동으로 폼 인코딩 방식으로 직렬화한다.
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    KakaoTokenResponse getToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code") String code
    );
}
