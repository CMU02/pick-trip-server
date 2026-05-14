package travel_agency.pick_trip.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfoResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record KakaoAccount(
            String email,
            Profile profile
    ) {
        public record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {}
    }

    public String providerUserId() {
        return String.valueOf(id);
    }

    // 이메일은 카카오 동의 항목이 선택 사항이므로 null일 수 있다.
    public String email() {
        return kakaoAccount != null ? kakaoAccount.email() : null;
    }

    // 프로필 정보는 카카오 동의 항목 미동의 시 kakaoAccount 또는 profile 자체가 null로 내려온다.
    public String nickname() {
        return kakaoAccount != null && kakaoAccount.profile() != null
                ? kakaoAccount.profile().nickname()
                : null;
    }

    public String profileImageUrl() {
        return kakaoAccount != null && kakaoAccount.profile() != null
                ? kakaoAccount.profile().profileImageUrl()
                : null;
    }
}
