package travel_agency.pick_trip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String authorizationCode
) {}
