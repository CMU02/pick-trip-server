package travel_agency.pick_trip.infra.ai.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiPlace")
class AiPlaceTest {

    @Test
    @DisplayName("candidate 는 id·이름·분류만 채우고 나머지는 null 로 둔다")
    void candidate_fillsOnlyIdTitleCategory() {
        // when
        AiPlace place = AiPlace.candidate("x9", "최참판댁", "12");

        // then
        assertThat(place.contentId()).isEqualTo("x9");
        assertThat(place.title()).isEqualTo("최참판댁");
        assertThat(place.category()).isEqualTo("12");
        assertThat(place.latitude()).isNull();
        assertThat(place.longitude()).isNull();
        assertThat(place.useTime()).isNull();
        assertThat(place.restDate()).isNull();
        assertThat(place.stayDuration()).isNull();
        assertThat(place.priority()).isNull();
        assertThat(place.desiredStayMinutes()).isNull();
    }
}
