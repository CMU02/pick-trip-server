package travel_agency.pick_trip.domain.auth.oauth2.userinfo;

import lombok.extern.slf4j.Slf4j;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;

import java.util.Map;

@Slf4j
public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    /**
     * @param registrationId application yaml 의 spring.security.oauth2.client.registration 키
     */
    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleUserInfo(attributes);
            case "kakao" -> new KakaoUserInfo(attributes);
            default -> {
                // registration 을 추가하고 여기에 분기를 빠뜨리면 발생한다.
                log.error("지원하지 않는 OAuth2 공급자입니다 - registrationId: {}", registrationId);
                throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
            }
        };
    }
}
