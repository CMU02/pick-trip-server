package travel_agency.pick_trip.gloal.jwt;

import io.jsonwebtoken.Claims;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class JwtUserPrincipal implements UserDetails {

    private final UUID uid;
    private final String role;

    private JwtUserPrincipal(UUID uid, String role) {
        this.uid = uid;
        this.role = role;
    }

    public static JwtUserPrincipal from(Claims claims) {
        return new JwtUserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("role", String.class)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return uid.toString();
    }

    @Override
    public String getPassword() {
        return null;
    }
}
