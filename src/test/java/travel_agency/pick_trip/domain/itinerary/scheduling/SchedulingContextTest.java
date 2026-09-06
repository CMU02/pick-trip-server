package travel_agency.pick_trip.domain.itinerary.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchedulingContext")
class SchedulingContextTest {

    private static SchedulingPlace place(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, false);
    }

    private static SchedulingPlace noCoordinates(String contentId) {
        return new SchedulingPlace(
                contentId, contentId, null, null, null, OperatingHours.unknown(), 90, false);
    }

    private static TravelMatrix matrixOf(String from, String to, double km, int minutes) {
        return new TravelMatrix(Map.of(TravelMatrix.key(from, to), new TravelMatrix.Leg(km, minutes)), 50.0);
    }

    @Test
    @DisplayName("도로 행렬에 값이 있으면 직선거리 대신 실측값을 쓴다.")
    void useRoadMatrixWhenAvailable() {
        // given
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.CAR, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(a, b);

        // then
        assertThat(leg.km()).isEqualTo(13.0);
        assertThat(leg.minutes()).isEqualTo(21);
    }

    @Test
    @DisplayName("도로 행렬에 없는 구간은 직선거리와 관측 평균 속도로 환산한다.")
    void fallbackToStraightDistanceWithObservedSpeed() {
        // given - a-b 만 실측했고 b-a 는 빠져 있다.
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.CAR, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(b, a);

        // then
        // 직선 약 11.1km * 1.3 = 14.5km, 관측 속도 50km/h 로 18분. 기본 35km/h 였다면 25분이다.
        assertThat(leg.km()).isCloseTo(11.1, org.assertj.core.data.Offset.offset(0.1));
        assertThat(leg.minutes()).isEqualTo(18);
    }

    @Test
    @DisplayName("대중교통 안은 자동차 도로 행렬을 쓰지 않는다.")
    void ignoreRoadMatrixForTransit() {
        // given
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.TRANSIT, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(a, b);

        // then
        assertThat(leg.km()).isNotEqualTo(13.0);
        assertThat(leg.minutes()).isGreaterThan(21);
    }

    @Test
    @DisplayName("좌표가 없는 장소가 끼면 구간을 계산하지 못한다.")
    void returnNullWhenCoordinatesMissing() {
        // given
        SchedulingContext context = SchedulingContext.car(null);

        // when
        TravelMatrix.Leg leg = context.legBetween(place("a", 35.0), noCoordinates("b"));

        // then
        assertThat(leg).isNull();
    }

    @Test
    @DisplayName("이동수단과 행렬을 주지 않으면 자동차·빈 행렬로 정규화한다.")
    void normalizeNullInputs() {
        // given
        SchedulingContext context = new SchedulingContext(null, null, null);

        // when
        TravelMode mode = context.travelMode();
        TravelMatrix matrix = context.travelMatrix();

        // then
        assertThat(mode).isEqualTo(TravelMode.CAR);
        assertThat(matrix.legs()).isEmpty();
        assertThat(matrix.fallbackSpeedKmh()).isEqualTo(SchedulingPolicy.AVG_SPEED_KMH);
    }
}
