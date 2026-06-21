package travel_agency.pick_trip.domain.share.dto.response;

import travel_agency.pick_trip.domain.share.entity.ShareToken;

/**
 * 공유 링크 생성 응답. 토큰과 공개 조회 경로를 반환한다.
 */
public record ShareCreateResponse(
        String token,
        String shareUrl
) {

    public static ShareCreateResponse from(ShareToken shareToken) {
        return new ShareCreateResponse(
                shareToken.getToken(),
                "/api/v1/share/" + shareToken.getToken()
        );
    }
}
