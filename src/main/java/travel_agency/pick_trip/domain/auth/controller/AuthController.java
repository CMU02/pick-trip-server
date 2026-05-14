package travel_agency.pick_trip.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.auth.dto.request.KakaoLoginRequest;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;

    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(kakaoAuthService.login(request.authorizationCode()));
    }
}
