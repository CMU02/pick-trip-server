package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.basket.repository.projection.BasketContentCountProjection;
import travel_agency.pick_trip.domain.content.client.VisitorStatsClient;
import travel_agency.pick_trip.domain.content.client.dto.RegionVisitorResponse;
import travel_agency.pick_trip.domain.content.dto.response.VisitorStatsResponse;
import travel_agency.pick_trip.domain.content.entity.RegionVisitorStats;
import travel_agency.pick_trip.domain.content.repository.RegionVisitorStatsRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("VisitorStatsService")
class VisitorStatsServiceTest {

    @Mock private VisitorStatsClient visitorStatsClient;
    @Mock private RegionVisitorStatsRepository regionVisitorStatsRepository;
    @Mock private BasketRepository basketRepository;

    @InjectMocks private VisitorStatsService visitorStatsService;

    private static final Region REGION = Region.YEONGJU; // 법정동 47 + 210
    private static final YearMonth MONTH = YearMonth.of(2026, 6);

    // --- 테스트 헬퍼 ---

    private RegionVisitorResponse visitorResponse(RegionVisitorResponse.Item... items) {
        return new RegionVisitorResponse(new RegionVisitorResponse.Response(
                new RegionVisitorResponse.Header("0000", "OK"),
                new RegionVisitorResponse.Body(new RegionVisitorResponse.Items(List.of(items)), 500, 1, 3)));
    }

    private RegionVisitorResponse.Item item(String touDivCd, Double touNum) {
        return new RegionVisitorResponse.Item(
                "20260601", "47", "47210", "영주시", touDivCd, "구분", touNum);
    }

    private RegionVisitorStats stats(String statMonth, long visitorCount) {
        return RegionVisitorStats.builder()
                .region(REGION)
                .statMonth(statMonth)
                .visitorCount(visitorCount)
                .source("한국관광공사 지역별 방문자수")
                .collectedAt(LocalDateTime.of(2026, 7, 2, 5, 0))
                .build();
    }

    private BasketContentCountProjection basketCount(String contentId, long count) {
        return new BasketContentCountProjection() {
            @Override
            public String getContentId() {
                return contentId;
            }

            @Override
            public long getBasketCount() {
                return count;
            }
        };
    }

    @Nested
    @DisplayName("collectRegion")
    class CollectRegion {

        @Test
        @DisplayName("현지인을 제외한 방문자수를 합산해 (지역, 연월) 로 저장한다")
        void collectRegion_응답합산_저장() {
            // given
            given(visitorStatsClient.getLocalRegionVisitors(
                    "20260601", "20260630", "47", "47210", 1, 500))
                    .willReturn(visitorResponse(item("1", 500.0), item("2", 1000.0), item("3", 250.4)));
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(REGION, "2026-06"))
                    .willReturn(Optional.empty());

            // when
            long collected = visitorStatsService.collectRegion(REGION, MONTH);

            // then
            assertThat(collected).isEqualTo(1250L);
            ArgumentCaptor<RegionVisitorStats> captor = ArgumentCaptor.forClass(RegionVisitorStats.class);
            verify(regionVisitorStatsRepository).save(captor.capture());
            assertThat(captor.getValue().getStatMonth()).isEqualTo("2026-06");
            assertThat(captor.getValue().getVisitorCount()).isEqualTo(1250L);
            assertThat(captor.getValue().getRegion()).isEqualTo(REGION);
        }

        @Test
        @DisplayName("이미 수집한 연월이면 새로 저장하지 않고 기존 행을 갱신한다")
        void collectRegion_기존행_갱신() {
            // given
            RegionVisitorStats existing = stats("2026-06", 10L);
            given(visitorStatsClient.getLocalRegionVisitors(
                    anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .willReturn(visitorResponse(item("2", 1000.0)));
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(REGION, "2026-06"))
                    .willReturn(Optional.of(existing));

            // when
            visitorStatsService.collectRegion(REGION, MONTH);

            // then
            assertThat(existing.getVisitorCount()).isEqualTo(1000L);
            verify(regionVisitorStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("외부 API 호출이 실패해도 예외를 던지지 않고 저장하지 않는다")
        void collectRegion_호출실패_폴백() {
            // given (인증키 활용신청 전이거나 한도 초과인 상황)
            given(visitorStatsClient.getLocalRegionVisitors(
                    anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .willThrow(new FeignException(500, "visitor-stats 5xx") {});

            // when
            long collected = visitorStatsService.collectRegion(REGION, MONTH);

            // then
            assertThat(collected).isZero();
            verify(regionVisitorStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("오류 결과코드(HTTP 200) 응답이면 저장하지 않는다")
        void collectRegion_오류코드_미저장() {
            // given
            given(visitorStatsClient.getLocalRegionVisitors(
                    anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .willReturn(new RegionVisitorResponse(new RegionVisitorResponse.Response(
                            new RegionVisitorResponse.Header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"),
                            null)));

            // when
            long collected = visitorStatsService.collectRegion(REGION, MONTH);

            // then
            assertThat(collected).isZero();
            verify(regionVisitorStatsRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findByContentIds")
    class FindByContentIds {

        @Test
        @DisplayName("지역 통계가 있으면 누적·일평균·기간을 매핑하고 근사 플래그를 세운다")
        void findByContentIds_지역통계_매핑() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of(stats("2026-06", 100_000L), stats("2026-05", 80_000L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1", "c2"));

            // then
            assertThat(result).containsOnlyKeys("c1", "c2");
            VisitorStatsResponse visitorStats = result.get("c1");
            assertThat(visitorStats.totalVisitors()).isEqualTo(180_000L);
            assertThat(visitorStats.dailyAverageVisitors()).isEqualTo(180_000L / 61);
            assertThat(visitorStats.period()).isEqualTo("2026-05~2026-06");
            assertThat(visitorStats.source()).isEqualTo("한국관광공사 지역별 방문자수");
            assertThat(visitorStats.baseDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            // 지역 단위 통계를 콘텐츠 값으로 그대로 내려주므로 항상 근사값이다.
            assertThat(visitorStats.approximate()).isTrue();
            verify(basketRepository, never()).countByContentIds(any());
        }

        @Test
        @DisplayName("지역 통계가 없으면 바구니에 담긴 횟수(자체 프록시)로 폴백한다")
        void findByContentIds_지역통계없음_프록시폴백() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of());
            given(basketRepository.countByContentIds(List.of("c1", "c2")))
                    .willReturn(List.of(basketCount("c1", 7L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1", "c2"));

            // then: 담긴 적 없는 c2 는 아예 빠져 응답 필드가 null 이 된다.
            assertThat(result).containsOnlyKeys("c1");
            assertThat(result.get("c1").totalVisitors()).isEqualTo(7L);
            assertThat(result.get("c1").dailyAverageVisitors()).isNull();
            assertThat(result.get("c1").period()).isNull();
            assertThat(result.get("c1").source()).isEqualTo("PickTrip 내부 지표");
            assertThat(result.get("c1").approximate()).isTrue();
        }

        @Test
        @DisplayName("지역 통계도 프록시도 없으면 빈 결과를 돌려준다")
        void findByContentIds_통계도프록시도없음_빈결과() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of());
            given(basketRepository.countByContentIds(List.of("c1"))).willReturn(List.of());

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1"));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("지역을 알 수 없으면 지역 통계를 조회하지 않고 프록시만 본다")
        void findByContentIds_지역모름_프록시만() {
            // given
            given(basketRepository.countByContentIds(List.of("c1")))
                    .willReturn(List.of(basketCount("c1", 3L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(null, List.of("c1"));

            // then
            assertThat(result.get("c1").source()).isEqualTo("PickTrip 내부 지표");
            verify(regionVisitorStatsRepository, never()).findByRegionOrderByStatMonthDesc(any());
        }
    }
}
