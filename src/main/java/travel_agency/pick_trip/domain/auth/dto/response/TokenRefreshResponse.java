package travel_agency.pick_trip.domain.auth.dto.response;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {}
