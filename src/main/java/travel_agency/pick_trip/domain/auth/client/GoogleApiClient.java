package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleUserInfoResponse;

@FeignClient(name = "google-api", url = "https://www.googleapis.com")
public interface GoogleApiClient {

    @GetMapping("/oauth2/v3/userinfo")
    GoogleUserInfoResponse getUserInfo(@RequestHeader("Authorization") String bearerToken);
}
