package travel_agency.pick_trip.domain.share.dto.response;

import travel_agency.pick_trip.domain.share.entity.ShareToken;

/**
 * 공유 링크 생성 응답. 토큰과 그대로 전달·복사할 수 있는 공유 링크를 반환한다.
 */
public record ShareCreateResponse(
        String token,
        String shareUrl
) {

    /**
     * @param linkBaseUrl 공유 링크 접두사 ({@code app.share.link-base-url}). 뒤에 {@code /토큰} 이 붙는다.
     */
    public static ShareCreateResponse from(ShareToken shareToken, String linkBaseUrl) {
        return new ShareCreateResponse(
                shareToken.getToken(),
                linkBaseUrl.replaceAll("/+$", "") + "/" + shareToken.getToken()
        );
    }
}
