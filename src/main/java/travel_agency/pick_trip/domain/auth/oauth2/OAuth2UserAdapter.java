package travel_agency.pick_trip.domain.auth.oauth2;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import travel_agency.pick_trip.domain.user.entity.User;

// 성공 핸들러에서 DB 재조회 없이 User 정보를 꺼낼 수 있도록 래핑한다.
public class OAuth2UserAdapter implements OAuth2User {

    @Getter
    private final User user;
    private final Map<String, Object> attributes;

    public OAuth2UserAdapter(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getName() {
        return user.getUid().toString();
    }
}
