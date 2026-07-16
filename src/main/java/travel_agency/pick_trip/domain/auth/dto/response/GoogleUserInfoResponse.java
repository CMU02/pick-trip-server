package travel_agency.pick_trip.domain.auth.dto.response;

public record GoogleUserInfoResponse(
        String sub,
        String email,
        String name,
        String picture
) {}
