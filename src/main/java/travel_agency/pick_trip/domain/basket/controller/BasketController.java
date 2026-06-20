package travel_agency.pick_trip.domain.basket.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.basket.dto.request.AddBasketItemRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketConditionsRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketItemPriorityRequest;
import travel_agency.pick_trip.domain.basket.dto.response.BasketItemResponse;
import travel_agency.pick_trip.domain.basket.dto.response.BasketResponse;
import travel_agency.pick_trip.domain.basket.service.BasketService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/baskets")
@RequiredArgsConstructor
public class BasketController {

    private final BasketService basketService;

    @GetMapping
    public ResponseEntity<BasketResponse> getBasket(
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(basketService.getBasket(principal.getUid()));
    }

    @PutMapping("/conditions")
    public ResponseEntity<BasketResponse> updateConditions(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody UpdateBasketConditionsRequest request
    ) {
        return ResponseEntity.ok(basketService.updateConditions(principal.getUid(), request));
    }

    @PostMapping("/items")
    public ResponseEntity<BasketItemResponse> addItem(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody AddBasketItemRequest request
    ) {
        BasketItemResponse response = basketService.addItem(principal.getUid(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<BasketItemResponse> changePriority(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateBasketItemPriorityRequest request
    ) {
        return ResponseEntity.ok(basketService.changePriority(principal.getUid(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itemId
    ) {
        basketService.removeItem(principal.getUid(), itemId);
        return ResponseEntity.noContent().build();
    }
}
