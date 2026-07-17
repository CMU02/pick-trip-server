package travel_agency.pick_trip.domain.auth.oauth2.userinfo;

import travel_agency.pick_trip.domain.user.entity.OAuthProvider;

/**
 * 공급자마다 다른 사용자 정보 응답을 공통 형태로 읽는다.
 *
 * <p>이메일·닉네임·프로필 이미지는 공급자 동의 항목에 따라 null 일 수 있다.
 */
public interface OAuth2UserInfo {

    OAuthProvider provider();

    /** 공급자 내에서 사용자를 식별하는 값. null 이면 로그인을 진행할 수 없다. */
    String providerUserId();

    String email();

    String nickname();

    String profileImageUrl();
}
