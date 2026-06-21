package travel_agency.pick_trip.domain.content.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.region.Region;

@DisplayName("TravelContent 엔티티")
class TravelContentTest {

    private TravelContent newContent() {
        return TravelContent.builder()
                .sourceContentId("126508")
                .contentTypeId("12")
                .title("하동 화개장터")
                .region(Region.HADONG)
                .build();
    }

    @Test
    @DisplayName("dataStatus를 지정하지 않으면 ACTIVE가 기본값이다")
    void dataStatus_기본값_ACTIVE() {
        TravelContent content = newContent();

        assertThat(content.getDataStatus()).isEqualTo(DataStatus.ACTIVE);
    }

    @Test
    @DisplayName("assignDetail은 1:1 상세를 양방향으로 연결한다")
    void assignDetail_양방향연결() {
        TravelContent content = newContent();
        ContentDetail detail = ContentDetail.builder()
                .useTime("09:00 ~ 18:00")
                .build();

        content.assignDetail(detail);

        assertThat(content.getDetail()).isSameAs(detail);
        assertThat(detail.getTravelContent()).isSameAs(content);
    }

    @Test
    @DisplayName("addImage는 이미지를 목록에 추가하고 양방향으로 연결한다")
    void addImage_양방향연결및목록추가() {
        TravelContent content = newContent();
        ContentImage image = ContentImage.builder()
                .source(ImageSource.TOUR_API)
                .imageUrl("https://tong.visitkorea.or.kr/image.jpg")
                .title("화개장터 전경")
                .build();

        content.addImage(image);

        assertThat(content.getImages()).containsExactly(image);
        assertThat(image.getTravelContent()).isSameAs(content);
    }

    @Test
    @DisplayName("changeDataStatus는 동기화 상태를 갱신한다")
    void changeDataStatus_갱신() {
        TravelContent content = newContent();

        content.changeDataStatus(DataStatus.INACTIVE);

        assertThat(content.getDataStatus()).isEqualTo(DataStatus.INACTIVE);
    }
}
