package travel_agency.pick_trip.domain.auth.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 구글은 "sub" 필드가 고유 사용자 식별자다.
        String providerUserId = (String) attributes.get("sub");
        if (providerUserId == null) {
            log.error("구글 OAuth2 사용자 정보에 sub 필드가 없습니다.");
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }
        String email = (String) attributes.get("email");
        String nickname = (String) attributes.get("name");
        String profileImageUrl = (String) attributes.get("picture");

        // 재로그인 시 닉네임·프로필 이미지를 최신 구글 정보로 동기화한다.
        // 이메일은 최초 가입 시만 저장하고 이후 변경하지 않는다.
        User user = userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .map(existing -> {
                    existing.updateProfile(nickname, profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(OAuthProvider.GOOGLE)
                        .providerUserId(providerUserId)
                        .email(email)
                        .nickname(nickname)
                        .profileImageUrl(profileImageUrl)
                        .build()));

        return new OAuth2UserAdapter(user, attributes);
    }
}
