package travel_agency.pick_trip.domain.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService")
class CustomOAuth2UserServiceTest {

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private UserRepository userRepository;

    private DefaultOAuth2UserService mockDelegate;
    private OAuth2UserRequest mockRequest;

    private static final String PROVIDER_ID = "google-sub-12345";
    private static final String EMAIL = "test@gmail.com";
    private static final String NICKNAME = "테스트유저";
    private static final String PROFILE_IMG = "https://img.google.com/profile.jpg";

    @BeforeEach
    void setUp() {
        // delegate는 final 필드로 직접 주입되므로 ReflectionTestUtils로 교체한다.
        mockDelegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", mockDelegate);
        mockRequest = mock(OAuth2UserRequest.class);
    }

    private OAuth2User mockOAuth2User(Map<String, Object> attributes) {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        given(oAuth2User.getAttributes()).willReturn(attributes);
        return oAuth2User;
    }

    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(PROVIDER_ID)
                .email(EMAIL)
                .nickname(NICKNAME)
                .profileImageUrl(PROFILE_IMG)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        return user;
    }

    @Nested
    @DisplayName("신규 구글 유저 가입")
    class NewUser {

        @Test
        @DisplayName("최초 구글 로그인 시 유저가 저장되고 OAuth2UserAdapter가 반환된다")
        void firstLogin_savesUserAndReturnsAdapter() {
            // given
            UUID uid = UUID.randomUUID();
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", EMAIL,
                    "name", NICKNAME, "picture", PROFILE_IMG
            );
            User savedUser = userWithUid(uid);

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            OAuth2User result = customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(result).isInstanceOf(OAuth2UserAdapter.class);
            then(userRepository).should().save(any(User.class));
        }
    }

    @Nested
    @DisplayName("기존 구글 유저 재로그인")
    class ExistingUser {

        @Test
        @DisplayName("재로그인 시 닉네임과 프로필 이미지가 최신 구글 정보로 업데이트된다")
        void reLogin_updatesNicknameAndProfileImage() {
            // given
            String newNickname = "변경된닉네임";
            String newProfileImg = "https://img.google.com/new.jpg";
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", EMAIL,
                    "name", newNickname, "picture", newProfileImg
            );
            User existingUser = userWithUid(UUID.randomUUID());

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(existingUser.getNickname()).isEqualTo(newNickname);
            assertThat(existingUser.getProfileImageUrl()).isEqualTo(newProfileImg);
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("재로그인 시 이메일은 변경하지 않는다")
        void reLogin_doesNotChangeEmail() {
            // given
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", "new-email@gmail.com",
                    "name", NICKNAME, "picture", PROFILE_IMG
            );
            User existingUser = userWithUid(UUID.randomUUID());
            String originalEmail = existingUser.getEmail();

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(existingUser.getEmail()).isEqualTo(originalEmail);
        }
    }

    @Nested
    @DisplayName("구글 OAuth2 오류")
    class OAuthError {

        @Test
        @DisplayName("구글 응답에 sub 필드가 없으면 AUTH_PROVIDER_ERROR 예외를 던진다")
        void missingSubField_throwsOAuthProviderException() {
            // given: sub 필드가 없는 응답 (HashMap으로 null 값 허용)
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("email", EMAIL);
            attributes.put("name", NICKNAME);

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);

            // when / then
            assertThatThrownBy(() -> customOAuth2UserService.loadUser(mockRequest))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
