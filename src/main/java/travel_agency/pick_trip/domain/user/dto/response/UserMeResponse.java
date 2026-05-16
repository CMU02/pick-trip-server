package travel_agency.pick_trip.domain.user.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserMeResponse(
        UUID uid,
        String email,
        String nickname,
        String profileImageUrl,
        String provider,
        LocalDateTime createdAt
) {
}
