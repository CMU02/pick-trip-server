package travel_agency.pick_trip.domain.auth.oauth2.userinfo;

import travel_agency.pick_trip.domain.user.entity.OAuthProvider;

import java.util.Map;

/**
 * 구글 user-info 응답을 읽는다.
 *
 * <p>구글은 모든 값을 최상위에 평면으로 내려준다.
 */
public record GoogleUserInfo(Map<String, Object> attributes) implements OAuth2UserInfo {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public String providerUserId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String email() {
        return (String) attributes.get("email");
    }

    @Override
    public String nickname() {
        return (String) attributes.get("name");
    }

    @Override
    public String profileImageUrl() {
        return (String) attributes.get("picture");
    }
}
