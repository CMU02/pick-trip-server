package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleTokenResponse;

@FeignClient(name = "google-auth", url = "https://oauth2.googleapis.com")
public interface GoogleAuthClient {

    // 구글 토큰 API는 카카오와 마찬가지로 application/x-www-form-urlencoded만 허용한다.
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    GoogleTokenResponse getToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code") String code
    );
}
