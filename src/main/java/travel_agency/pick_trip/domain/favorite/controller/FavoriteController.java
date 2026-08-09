package travel_agency.pick_trip.domain.favorite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.service.FavoriteService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<FavoritesResponse> getFavorites(
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(favoriteService.getFavorites(principal.getUid()));
    }

    @PostMapping
    public ResponseEntity<FavoriteResponse> addFavorite(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody AddFavoriteRequest request
    ) {
        FavoriteResponse response = favoriteService.addFavorite(principal.getUid(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable String contentId
    ) {
        favoriteService.removeFavorite(principal.getUid(), contentId);
        return ResponseEntity.noContent().build();
    }
}
