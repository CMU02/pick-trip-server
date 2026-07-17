package travel_agency.pick_trip.domain.auth.oauth2.userinfo;

import travel_agency.pick_trip.domain.user.entity.OAuthProvider;

import java.util.Map;

/**
 * 카카오 user-info 응답을 읽는다.
 *
 * <p>카카오는 식별자만 최상위(id)에 두고 나머지는 kakao_account 아래에 중첩한다.
 * 동의 항목에 동의하지 않으면 kakao_account 나 profile 자체가 내려오지 않으므로
 * 각 단계에서 null 을 확인한다.
 */
public record KakaoUserInfo(Map<String, Object> attributes) implements OAuth2UserInfo {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public String providerUserId() {
        // 카카오 id 는 문자열이 아닌 숫자로 내려온다.
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @Override
    public String email() {
        return (String) valueOf(kakaoAccount(), "email");
    }

    @Override
    public String nickname() {
        return (String) valueOf(profile(), "nickname");
    }

    @Override
    public String profileImageUrl() {
        return (String) valueOf(profile(), "profile_image_url");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> kakaoAccount() {
        return (Map<String, Object>) attributes.get("kakao_account");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> profile() {
        Map<String, Object> account = kakaoAccount();
        return account != null ? (Map<String, Object>) account.get("profile") : null;
    }

    private Object valueOf(Map<String, Object> source, String key) {
        return source != null ? source.get(key) : null;
    }
}
