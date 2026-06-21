package travel_agency.pick_trip.domain.share.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.share.dto.response.ShareCreateResponse;
import travel_agency.pick_trip.domain.share.dto.response.SharedItineraryResponse;
import travel_agency.pick_trip.domain.share.service.ShareService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 일정의 공유 링크(토큰)를 생성한다. 소유자만 호출 가능.
     */
    @PostMapping("/itineraries/{itineraryId}/share")
    public ResponseEntity<ShareCreateResponse> createShare(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itineraryId
    ) {
        ShareCreateResponse response = shareService.createShare(principal.getUid(), itineraryId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 공유 토큰으로 공개 일정을 조회한다 (인증 불필요).
     */
    @GetMapping("/share/{token}")
    public ResponseEntity<SharedItineraryResponse> getSharedItinerary(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(shareService.getSharedItinerary(token));
    }
}
