package travel_agency.pick_trip.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.auth.dto.request.TokenRefreshRequest;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.TokenService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

/**
 * 소셜 로그인 시작·콜백은 Spring Security 의 oauth2Login 이 처리하므로 여기에 없다.
 * 시작: GET /oauth2/authorization/{kakao|google}, 콜백: GET /login/oauth2/code/{kakao|google}
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(tokenService.refresh(request.refreshToken()));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtUserPrincipal principal) {
        tokenService.logout(principal.getUid());
        return ResponseEntity.noContent().build();
    }
}
