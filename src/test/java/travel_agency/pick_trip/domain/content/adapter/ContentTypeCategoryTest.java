package travel_agency.pick_trip.domain.content.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentTypeCategory")
class ContentTypeCategoryTest {

    @Nested
    @DisplayName("resolveContentTypeId")
    class ResolveContentTypeId {

        @Test
        @DisplayName("explicit contentTypeId가 있으면 그대로 반환한다")
        void explicitContentTypeId_returnsAsIs() {
            String result = ContentTypeCategory.resolveContentTypeId("28", null, null);
            assertThat(result).isEqualTo("28");
        }

        @Test
        @DisplayName("indoorOnly=true이고 contentTypeId 없으면 문화시설(14) 반환")
        void indoorOnly_true_returnsCulture() {
            String result = ContentTypeCategory.resolveContentTypeId(null, true, null);
            assertThat(result).isEqualTo("14");
        }

        @Test
        @DisplayName("indoorOnly=false이고 contentTypeId 없으면 관광지(12) 반환")
        void indoorOnly_false_returnsTourism() {
            String result = ContentTypeCategory.resolveContentTypeId(null, false, null);
            assertThat(result).isEqualTo("12");
        }

        @Test
        @DisplayName("companion=FAMILY이고 contentTypeId 없으면 문화시설(14) 반환")
        void companion_family_returnsCulture() {
            String result = ContentTypeCategory.resolveContentTypeId(null, null, CompanionType.FAMILY);
            assertThat(result).isEqualTo("14");
        }

        @Test
        @DisplayName("companion=COUPLE이고 contentTypeId 없으면 null 반환")
        void companion_couple_returnsNull() {
            String result = ContentTypeCategory.resolveContentTypeId(null, null, CompanionType.COUPLE);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("indoorOnly와 companion 둘 다 있으면 indoorOnly 우선 적용")
        void indoorOnly_takesOverCompanion() {
            String result = ContentTypeCategory.resolveContentTypeId(null, false, CompanionType.FAMILY);
            assertThat(result).isEqualTo("12");
        }

        @Test
        @DisplayName("explicit contentTypeId가 있으면 indoorOnly와 companion 무시")
        void explicit_ignoresFilters() {
            String result = ContentTypeCategory.resolveContentTypeId("39", true, CompanionType.FAMILY);
            assertThat(result).isEqualTo("39");
        }
    }

    @Nested
    @DisplayName("stayDurationFor")
    class StayDurationFor {

        @Test
        @DisplayName("관광지(12)는 약 2시간 반환")
        void tourism_returns2Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(12)).isEqualTo("약 2시간");
        }

        @Test
        @DisplayName("문화시설(14)은 약 1~2시간 반환")
        void culture_returns1To2Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(14)).isEqualTo("약 1~2시간");
        }

        @Test
        @DisplayName("레포츠(28)은 약 2~3시간 반환")
        void leisure_returns2To3Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(28)).isEqualTo("약 2~3시간");
        }

        @Test
        @DisplayName("음식점(39)은 약 1시간 반환")
        void restaurant_returns1Hour() {
            assertThat(ContentTypeCategory.stayDurationFor(39)).isEqualTo("약 1시간");
        }

        @Test
        @DisplayName("알 수 없는 contentTypeId는 null 반환")
        void unknown_returnsNull() {
            assertThat(ContentTypeCategory.stayDurationFor(99)).isNull();
        }
    }
}
