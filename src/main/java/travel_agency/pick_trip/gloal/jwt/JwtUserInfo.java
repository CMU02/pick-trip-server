package travel_agency.pick_trip.gloal.jwt;

import java.util.UUID;

public record JwtUserInfo(UUID uid, String nickname, String email, String role) {}
