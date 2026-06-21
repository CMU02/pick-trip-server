package travel_agency.pick_trip.domain.share.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.share.dto.response.ShareCreateResponse;
import travel_agency.pick_trip.domain.share.dto.response.SharedItineraryResponse;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.gloal.error.exception.ShareException;

/**
 * 일정 공유 유스케이스.
 * 소유자만 공유 링크를 생성할 수 있고, 공개 조회는 활성 토큰으로만 가능하다.
 */
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareTokenRepository shareTokenRepository;
    private final ItineraryRepository itineraryRepository;

    /**
     * 일정의 공유 링크를 생성한다. 이미 활성 토큰이 있으면 재사용한다(멱등).
     */
    @Transactional
    public ShareCreateResponse createShare(UUID userId, UUID itineraryId) {
        Itinerary itinerary = findOwned(userId, itineraryId);
        ShareToken shareToken = shareTokenRepository.findByItineraryIdAndActiveTrue(itinerary.getItineraryId())
                .orElseGet(() -> shareTokenRepository.save(ShareToken.builder()
                        .itineraryId(itinerary.getItineraryId())
                        .token(generateToken())
                        .build()));
        return ShareCreateResponse.from(shareToken);
    }

    /**
     * 공유 토큰으로 공개 일정을 조회한다(비로그인 허용).
     */
    @Transactional(readOnly = true)
    public SharedItineraryResponse getSharedItinerary(String token) {
        ShareToken shareToken = shareTokenRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() -> new ShareException(ErrorCode.SHARE_ITINERARY_NOT_FOUND));
        Itinerary itinerary = itineraryRepository.findWithDaysById(shareToken.getItineraryId())
                .orElseThrow(() -> new ShareException(ErrorCode.SHARE_ITINERARY_NOT_FOUND));
        return SharedItineraryResponse.from(itinerary);
    }

    private Itinerary findOwned(UUID userId, UUID itineraryId) {
        Itinerary itinerary = itineraryRepository.findWithDaysById(itineraryId)
                .orElseThrow(() -> new ItineraryException(ErrorCode.ITINERARY_NOT_FOUND));
        if (!itinerary.isOwnedBy(userId)) {
            throw new ItineraryException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }

    /**
     * 예측 불가능한 공유 토큰을 생성한다 (UUID 기반, 하이픈 제거 32자).
     */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
