package travel_agency.pick_trip.domain.content.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.KakaoMobilityClient;
import travel_agency.pick_trip.domain.content.client.dto.KakaoDirectionsResponse;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;
import travel_agency.pick_trip.domain.itinerary.scheduling.GeoDistance;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPolicy;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMatrix;

/**
 * 일정에 들어갈 장소들 사이의 실제 자동차 도로 거리·소요 시간 행렬을 Kakao Mobility 길찾기로 만든다.
 *
 * <p>여러 목적지 길찾기는 1 origin → 최대 30 목적지를 1콜로 처리하므로, 장소 s개의 전체 행렬은
 * origin 당 1콜씩 총 s콜이면 된다. 인접 구간만 뽑는 것과 콜 수가 거의 같으면서 순서 최적화에도 쓸 수 있다.
 *
 * <p>요청의 {@code radius} 상한이 10km 라 기준점에서 그보다 먼 목적지는 응답에서 빠진다. 소도시 관광지 쌍의 절반 이상이
 * 여기 해당하고, 산·강 때문에 실제 도로는 직선의 1.2~2.6배로 들쭉날쭉해 직선거리 환산으로는 순서 판단이 뒤집힌다(#97).
 * 그래서 빠진 쌍은 단건 길찾기로 다시 채운다.
 *
 * <p>길찾기 실패·타임아웃은 예외로 올리지 않는다. 경고만 남기고 빈 행렬 또는 부분 행렬을 반환하며,
 * 재시도하지 않는다(사용량 절약). 채우지 못한 구간은 스케줄러가 직선거리 환산으로 폴백한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadMatrixResolver {

    /** Kakao Mobility 여러 목적지 길찾기 {@code radius} 상한(m). */
    private static final int KAKAO_RADIUS_MAX_METERS = 10_000;

    /**
     * 한 번의 일정 생성에서 실측할 장소 수 상한(= 콜 수 상한. origin 당 1콜이다).
     * 1박 2일~2박 3일 바구니는 보통 열 곳 안팎이라 15면 대부분을 덮으면서,
     * 여러 목적지 API 의 목적지 상한(30)도 자연히 지킨다(장소 15개 → 목적지 14개).
     * 이보다 많으면 콜 수 대비 이득이 적어 앞쪽 장소까지만 실측하고 나머지 구간은 직선거리로 둔다.
     */
    private static final int MAX_ORIGIN_CALLS = 15;

    /**
     * 반경 밖 구간 단건 길찾기를 동시에 보내는 수. 장소 열 곳이면 빠지는 쌍이 스무 개 안팎이라 순차로는 수 초가 더 걸린다.
     * shortcut: Kakao 의 초당 호출 제한을 확인하지 못해 보수적으로 4로 둔다. 429 가 보이면 낮추고, 여유가 확인되면 올린다.
     */
    private static final int FAR_LEG_CONCURRENCY = 4;

    private final KakaoMobilityClient kakaoMobilityClient;

    private record Pair(SchedulingPlace from, SchedulingPlace to) {
    }

    private record Measured(Pair pair, KakaoMultiDestResponse.Summary summary) {
    }

    /**
     * @param places 좌표가 있는 장소만 실측 대상이 된다. 두 곳 미만이면 조회할 구간이 없어 빈 행렬을 반환한다.
     */
    public TravelMatrix resolve(Collection<SchedulingPlace> places) {
        List<SchedulingPlace> targets = places.stream()
                .filter(place -> place != null && place.hasCoordinates())
                .limit(MAX_ORIGIN_CALLS)
                .toList();
        if (targets.size() < 2) {
            return TravelMatrix.empty();
        }

        Map<String, TravelMatrix.Leg> legs = new HashMap<>();
        long totalMeters = 0;
        long totalSeconds = 0;
        boolean multiDestFailed = false;

        for (SchedulingPlace origin : targets) {
            List<SchedulingPlace> destinations = targets.stream()
                    .filter(place -> !place.contentId().equals(origin.contentId()))
                    .toList();
            KakaoMultiDestResponse response = fetch(origin, destinations);
            if (response == null) {
                // 한 콜이 실패하면 나머지도 같은 이유로 실패할 가능성이 높다. 남은 콜로 응답만 늦추지 않고
                // 여기까지 모은 부분 행렬로 진행한다(재시도도 하지 않는다).
                multiDestFailed = true;
                break;
            }
            for (KakaoMultiDestResponse.Route route : response.routes()) {
                if (!isSuccess(route)) {
                    continue;
                }
                KakaoMultiDestResponse.Summary summary = route.summary();
                legs.put(TravelMatrix.key(origin.contentId(), route.key()), toLeg(summary));
                totalMeters += summary.distance();
                totalSeconds += summary.duration() == null ? 0 : summary.duration();
            }
        }

        // 길찾기 서버가 실패 중이면 단건 콜도 같은 이유로 실패하므로 채우지 않는다.
        if (!multiDestFailed) {
            for (Measured measured : fetchEach(mirrorAndCollectUnmeasured(targets, legs))) {
                TravelMatrix.Leg leg = toLeg(measured.summary());
                legs.put(TravelMatrix.key(measured.pair().from().contentId(), measured.pair().to().contentId()), leg);
                legs.put(TravelMatrix.key(measured.pair().to().contentId(), measured.pair().from().contentId()), leg);
                totalMeters += measured.summary().distance();
                totalSeconds += measured.summary().duration() == null ? 0 : measured.summary().duration();
            }
        }

        if (legs.isEmpty()) {
            log.warn("[일정] 도로 거리 행렬을 하나도 확보하지 못해 직선거리로 진행합니다. 장소={}건", targets.size());
            return TravelMatrix.empty();
        }
        return new TravelMatrix(legs, observedSpeedKmh(totalMeters, totalSeconds));
    }

    /**
     * 한쪽 방향만 실측된 쌍은 그 값을 반대쪽에도 쓰고, 양쪽 다 빠진 쌍만 돌려준다.
     * 도로 거리는 일방통행 때문에 방향마다 조금 다르지만, 콜 수를 절반으로 줄이려고 한쪽 실측을 양방향에 쓴다.
     */
    private static List<Pair> mirrorAndCollectUnmeasured(List<SchedulingPlace> targets,
                                                         Map<String, TravelMatrix.Leg> legs) {
        List<Pair> unmeasured = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            for (int j = i + 1; j < targets.size(); j++) {
                String forward = TravelMatrix.key(targets.get(i).contentId(), targets.get(j).contentId());
                String backward = TravelMatrix.key(targets.get(j).contentId(), targets.get(i).contentId());
                TravelMatrix.Leg forwardLeg = legs.get(forward);
                TravelMatrix.Leg backwardLeg = legs.get(backward);
                if (forwardLeg == null && backwardLeg == null) {
                    unmeasured.add(new Pair(targets.get(i), targets.get(j)));
                } else if (forwardLeg == null) {
                    legs.put(forward, backwardLeg);
                } else if (backwardLeg == null) {
                    legs.put(backward, forwardLeg);
                }
            }
        }
        return unmeasured;
    }

    /**
     * 쌍마다 단건 길찾기를 병렬로 보낸다. 경로가 없는 쌍은 건너뛰고, 호출 자체가 실패하면 남은 콜은 보내지 않는다.
     * shortcut: 요청마다 작은 스레드 풀을 만든다. 일정 생성 트래픽이 늘면 공용 풀로 옮긴다.
     */
    private List<Measured> fetchEach(List<Pair> pairs) {
        if (pairs.isEmpty()) {
            return List.of();
        }
        Queue<Measured> measured = new ConcurrentLinkedQueue<>();
        AtomicBoolean failed = new AtomicBoolean();
        try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(FAR_LEG_CONCURRENCY, pairs.size()))) {
            for (Pair pair : pairs) {
                pool.execute(() -> {
                    if (failed.get()) {
                        return;
                    }
                    try {
                        KakaoMultiDestResponse.Summary summary = successSummary(kakaoMobilityClient.getDirections(
                                coordinate(pair.from()), coordinate(pair.to()), true));
                        if (summary != null) {
                            measured.add(new Measured(pair, summary));
                        }
                    } catch (RuntimeException e) {
                        if (failed.compareAndSet(false, true)) {
                            log.warn("[일정] Kakao Mobility 단건 길찾기 실패 - 남은 먼 구간은 직선거리로 폴백: {}",
                                    e.getMessage());
                        }
                    }
                });
            }
        }
        return List.copyOf(measured);
    }

    /** Kakao 좌표 파라미터 형식({@code "경도,위도"}). */
    private static String coordinate(SchedulingPlace place) {
        return place.longitude() + "," + place.latitude();
    }

    private static KakaoMultiDestResponse.Summary successSummary(KakaoDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return null;
        }
        KakaoDirectionsResponse.Route route = response.routes().get(0);
        boolean success = route != null && route.resultCode() != null && route.resultCode() == 0
                && route.summary() != null && route.summary().distance() != null;
        return success ? route.summary() : null;
    }

    /** 실패·타임아웃은 예외로 올리지 않고 null 로 알린다. */
    private KakaoMultiDestResponse fetch(SchedulingPlace origin, List<SchedulingPlace> destinations) {
        KakaoMultiDestRequest request = new KakaoMultiDestRequest(
                new KakaoMultiDestRequest.Point(origin.longitude(), origin.latitude()),
                destinations.stream()
                        .map(d -> new KakaoMultiDestRequest.Destination(
                                d.longitude(), d.latitude(), d.contentId()))
                        .toList(),
                radiusMetersFor(origin, destinations)
        );

        try {
            KakaoMultiDestResponse response = kakaoMobilityClient.getMultiDestinationDirections(request);
            return (response == null || response.routes() == null) ? null : response;
        } catch (RuntimeException e) {
            log.warn("[일정] Kakao Mobility 길찾기 실패 - 직선거리로 폴백: {}", e.getMessage());
            return null;
        }
    }

    /** 가장 먼 목적지까지 덮도록 반경을 잡되 API 상한(10km)으로 제한한다. 그 밖의 목적지는 응답에서 빠진다. */
    private static int radiusMetersFor(SchedulingPlace origin, List<SchedulingPlace> destinations) {
        double maxKm = destinations.stream()
                .mapToDouble(d -> GeoDistance.kilometers(
                        origin.latitude(), origin.longitude(), d.latitude(), d.longitude()))
                .max()
                .orElse(0.0);
        int radius = (int) Math.ceil(maxKm * 1000.0) + 100;
        return Math.min(Math.max(radius, 100), KAKAO_RADIUS_MAX_METERS);
    }

    /**
     * 실측에 성공한 구간 전체의 평균 속도(도로 거리 합 ÷ 소요 시간 합).
     * 반경 밖이라 빠진 구간을 직선거리로 환산할 때 이 지역에서 실제로 관측된 속도를 쓰기 위함이다.
     * 성공 구간이 없거나 소요 시간이 0이면 정책 기본 속도를 쓴다.
     */
    private static double observedSpeedKmh(long totalMeters, long totalSeconds) {
        if (totalSeconds <= 0) {
            return SchedulingPolicy.AVG_SPEED_KMH;
        }
        return (totalMeters / 1000.0) / (totalSeconds / 3600.0);
    }

    private static boolean isSuccess(KakaoMultiDestResponse.Route route) {
        return route != null
                && route.key() != null
                && route.resultCode() != null && route.resultCode() == 0
                && route.summary() != null && route.summary().distance() != null;
    }

    private static TravelMatrix.Leg toLeg(KakaoMultiDestResponse.Summary summary) {
        double km = summary.distance() / 1000.0;
        // 소요 시간이 없으면 거리만으로 환산한다. 거리가 있는 구간을 0분으로 두면 같은 시각에 두 장소에 있게 된다.
        int minutes = summary.duration() != null
                ? Math.max((int) Math.round(summary.duration() / 60.0), 1)
                : Math.max((int) Math.ceil(km / SchedulingPolicy.AVG_SPEED_KMH * 60), 1);
        return new TravelMatrix.Leg(km, minutes);
    }
}
