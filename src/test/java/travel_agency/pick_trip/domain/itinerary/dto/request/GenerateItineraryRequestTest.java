package travel_agency.pick_trip.domain.itinerary.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;

@DisplayName("GenerateItineraryRequest")
class GenerateItineraryRequestTest {

    @Test
    @DisplayName("이동수단을 지정하지 않으면 자동차 단일안으로 정규화한다.")
    void defaultsToCarWhenModesMissing() {
        // when
        GenerateItineraryRequest request = GenerateItineraryRequest.defaults();

        // then
        assertThat(request.travelModes()).containsExactly(TravelMode.CAR);
        assertThat(request.mode()).isEqualTo(GenerateMode.STRICT);
    }

    @Test
    @DisplayName("빈 리스트나 null 만 담긴 리스트도 자동차 단일안으로 되돌린다.")
    void fallbackToCarForEmptyModes() {
        // given
        List<TravelMode> onlyNulls = Arrays.asList(null, null);

        // when
        GenerateItineraryRequest empty = new GenerateItineraryRequest(null, null, List.of());
        GenerateItineraryRequest nulls = new GenerateItineraryRequest(null, null, onlyNulls);

        // then
        assertThat(empty.travelModes()).containsExactly(TravelMode.CAR);
        assertThat(nulls.travelModes()).containsExactly(TravelMode.CAR);
    }

    @Test
    @DisplayName("중복 이동수단은 순서를 유지한 채 한 번만 남긴다.")
    void removeDuplicateModes() {
        // given
        List<TravelMode> requested = List.of(TravelMode.TRANSIT, TravelMode.CAR, TravelMode.TRANSIT);

        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(null, null, requested);

        // then
        assertThat(request.travelModes()).containsExactly(TravelMode.TRANSIT, TravelMode.CAR);
    }

    @Test
    @DisplayName("일정안 수는 상한을 넘지 않는다.")
    void limitVariantCount() {
        // given - 이동수단이 늘어나도 상한 안으로 잘린다. 지금은 두 종류뿐이라 중복 제거만으로도 상한 안이다.
        List<TravelMode> requested = List.of(
                TravelMode.CAR, TravelMode.TRANSIT, TravelMode.CAR, TravelMode.TRANSIT, TravelMode.CAR);

        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(null, null, requested);

        // then
        assertThat(request.travelModes())
                .hasSizeLessThanOrEqualTo(GenerateItineraryRequest.MAX_VARIANTS)
                .containsExactly(TravelMode.CAR, TravelMode.TRANSIT);
    }

    @Test
    @DisplayName("이동수단 목록은 수정할 수 없다.")
    void travelModesAreImmutable() {
        // given
        GenerateItineraryRequest request =
                new GenerateItineraryRequest(null, null, List.of(TravelMode.CAR));

        // when
        List<TravelMode> modes = request.travelModes();

        // then
        assertThat(modes).isUnmodifiable();
    }

    @Test
    @DisplayName("dayStartTimes 를 지정하지 않으면 빈 리스트로 정규화한다")
    void dayStartTimesDefaultsToEmptyList() {
        // when
        GenerateItineraryRequest nullCase = new GenerateItineraryRequest(null, null, null, null);
        GenerateItineraryRequest emptyCase = new GenerateItineraryRequest(null, null, null, List.of());

        // then
        assertThat(nullCase.dayStartTimes()).isEmpty();
        assertThat(emptyCase.dayStartTimes()).isEmpty();
    }

    @Test
    @DisplayName("원소가 null 이면 '그 일차만 기본값'을 뜻하므로 null 그대로 유지한다")
    void dayStartTimesKeepsNullElements() {
        // given
        List<LocalTime> requested = Arrays.asList(LocalTime.of(10, 30), null);

        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(null, null, null, requested);

        // then
        assertThat(request.dayStartTimes()).containsExactly(LocalTime.of(10, 30), null);
    }

    @Test
    @DisplayName("05:00~18:00 범위 안이면 통과한다")
    void dayStartTimesWithinRangePasses() {
        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(
                null, null, null, List.of(GenerateItineraryRequest.DAY_START_MIN, GenerateItineraryRequest.DAY_START_MAX));

        // then
        assertThat(request.dayStartTimes()).containsExactly(LocalTime.of(5, 0), LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("04:59 처럼 범위보다 이르면 IllegalArgumentException")
    void dayStartTimesBeforeMinThrows() {
        // when
        ThrowingCallable action = () -> new GenerateItineraryRequest(null, null, null, List.of(LocalTime.of(4, 59)));

        // then
        assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("18:01 처럼 범위보다 늦으면 IllegalArgumentException")
    void dayStartTimesAfterMaxThrows() {
        // when
        ThrowingCallable action = () -> new GenerateItineraryRequest(null, null, null, List.of(LocalTime.of(18, 1)));

        // then
        assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기존 3-인자 생성자와 defaults() 는 dayStartTimes 가 빈 리스트다")
    void legacyConstructorsHaveEmptyDayStartTimes() {
        // when
        GenerateItineraryRequest threeArg = new GenerateItineraryRequest(null, null, List.of(TravelMode.CAR));
        GenerateItineraryRequest defaults = GenerateItineraryRequest.defaults();

        // then
        assertThat(threeArg.dayStartTimes()).isEmpty();
        assertThat(defaults.dayStartTimes()).isEmpty();
    }
}
