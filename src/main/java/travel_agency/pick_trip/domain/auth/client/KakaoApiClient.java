package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoUserInfoResponse;

@FeignClient(name = "kakao-api", url = "https://kapi.kakao.com")
public interface KakaoApiClient {

    @GetMapping("/v2/user/me")
    KakaoUserInfoResponse getUserInfo(@RequestHeader("Authorization") String bearerToken);
}
