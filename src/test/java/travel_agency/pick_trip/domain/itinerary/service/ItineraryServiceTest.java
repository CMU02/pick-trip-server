package travel_agency.pick_trip.domain.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Collection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.RegionContentProjection;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;
import travel_agency.pick_trip.domain.content.service.CongestionService;
import travel_agency.pick_trip.domain.content.service.ContentService;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateMode;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.itinerary.scheduling.ItineraryPlanner;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;
import travel_agency.pick_trip.infra.ai.AiItineraryClient;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiDay;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiItem;
import travel_agency.pick_trip.infra.ai.dto.AiPlace;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryService")
class ItineraryServiceTest {

    @Mock private BasketRepository basketRepository;
    @Mock private ContentService contentService;
    @Mock private TravelContentRepository travelContentRepository;
    @Mock private AiItineraryClient aiItineraryClient;
    @Mock private ItineraryRepository itineraryRepository;
    @Mock private ShareTokenRepository shareTokenRepository;
    @Mock private CongestionService congestionService;
    @InjectMocks private ItineraryService itineraryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ITINERARY_ID = UUID.randomUUID();

    // --- 테스트 헬퍼 ---

    private Basket basketWith(Region region, Integer duration, String... contentIds) {
        Basket basket = Basket.builder().userId(USER_ID).build();
        basket.updateConditions(region, LocalDate.of(2026, 7, 1), duration, Set.of(TravelCondition.WITH_CHILD));
        for (String contentId : contentIds) {
            basket.addItem(BasketItem.builder()
                    .contentId(contentId)
                    .title("title-" + contentId)
                    .contentTypeId("12")
                    .priority(Priority.MUST_VISIT)
                    .build());
        }
        return basket;
    }

    private ContentDetailResponse detail(String contentId) {
        return new ContentDetailResponse(
                contentId, "title-" + contentId, 12, "주소", "010", "home",
                35.0, 127.0, "요약", "09:00-18:00", "월요일", "가능", "무료",
                "없음", "불가", "2시간", Boolean.FALSE, "TourAPI", List.of(),
                ContentCategory.ATTRACTION, false, "HADONG", null
        );
    }

    private AiItineraryResult twoPlaceResult() {
        return new AiItineraryResult(
                "하동 1박 2일 가족 여행",
                List.of(new AiDay(1, List.of(
                        new AiItem("c1", 1, "오전 운영시간에 맞춰 배치했습니다."),
                        new AiItem("c2", 2, "동선상 인접해 오후에 배치했습니다.")
                )))
        );
    }

    private Itinerary itineraryOwnedBy(UUID ownerId) {
        Itinerary itinerary = Itinerary.builder()
                .userId(ownerId)
                .title("기존 제목")
                .region(Region.HADONG)
                .travelDate(LocalDate.of(2026, 7, 1))
                .duration(2)
                .build();
        ItineraryDay day = ItineraryDay.builder().dayIndex(1).build();
        day.addItem(ItineraryItem.builder()
                .contentId("c1").title("title-c1").orderIndex(1).reason("기존 이유").pinned(false).build());
        itinerary.addDay(day);
        return itinerary;
    }

    private Itinerary mockSummaryItinerary(UUID itineraryId, String title, LocalDateTime lastModifiedAt) {
        Itinerary itinerary = mock(Itinerary.class);
        given(itinerary.getItineraryId()).willReturn(itineraryId);
        given(itinerary.getTitle()).willReturn(title);
        given(itinerary.getRegion()).willReturn(Region.HADONG);
        given(itinerary.getTravelDate()).willReturn(LocalDate.of(2026, 7, 1));
        given(itinerary.getDuration()).willReturn(2);
        given(itinerary.getLastModifiedAt()).willReturn(lastModifiedAt);
        return itinerary;
    }

    private SaveItineraryRequest saveRequest() {
        return new SaveItineraryRequest(
                "새 제목", Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                List.of(new SaveItineraryRequest.DayRequest(1, List.of(
                        new SaveItineraryRequest.ItemRequest(
                                "c1", "title-c1", 1, "이유1", true,
                                LocalTime.of(9, 0), LocalTime.of(10, 30)),
                        new SaveItineraryRequest.ItemRequest(
                                "c2", "title-c2", 2, "이유2", false,
                                LocalTime.of(11, 0), LocalTime.of(12, 30))
                ), 25, new BigDecimal("12.30")))
        );
    }

    @Nested
    @DisplayName("generate - 입력 검증")
    class ValidateInput {

        @Test
        @DisplayName("바구니가 없으면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void noBasket_throws() {
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("콘텐츠가 2개 미만이면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void lessThanTwoContents_throws() {
            Basket basket = basketWith(Region.HADONG, 2, "c1");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("여행 기간(duration)이 없으면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void noDuration_throws() {
            Basket basket = basketWith(Region.HADONG, null, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }
    }

    @Nested
    @DisplayName("generate - 정상 흐름")
    class Generate {

        @Test
        @DisplayName("콘텐츠 상세를 보강해 AI를 호출하고, 장소명은 바구니 스냅샷에서 매핑한다")
        void enrichesDetailAndMapsResponse() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(response.region()).isEqualTo(Region.HADONG);
            assertThat(response.duration()).isEqualTo(2);
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
            assertThat(response.days().get(0).items().get(0).title()).isEqualTo("title-c1");
            assertThat(response.days().get(0).items().get(0).reason()).contains("운영시간");

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            AiItineraryRequest request = captor.getValue();
            assertThat(request.places()).hasSize(2);
            assertThat(request.places().get(0).latitude()).isEqualTo(35.0);
            assertThat(request.regionName()).isEqualTo("하동");
            assertThat(request.companions()).containsExactly("아이와 함께");
            assertThat(request.places().get(0).priority()).isEqualTo("꼭 가기");
        }

        @Test
        @DisplayName("AI 배치 이유에 새어 나온 contentId·enum 코드를 응답에서 제거한다")
        void sanitizesLeakedInternalValuesInReason() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "슬로시티(773075)와 가깝고 LESS_WALKING 조건이라 먼저 배치했습니다."),
                            new AiItem("c2", 2, "동선상 인접해 오후에 배치했습니다.")
                    )))
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            String firstReason = response.days().get(0).items().get(0).reason();
            assertThat(firstReason)
                    .doesNotContain("773075")
                    .doesNotContain("LESS_WALKING")
                    .contains("슬로시티")
                    .contains("걷기 적게");
        }

        @Test
        @DisplayName("생성 결과의 각 장소에 방문 시각이 배정되고 첫 스톱은 09:00에 시작한다")
        void assignsVisitTimes() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            assertThat(items.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(items).allSatisfy(item -> {
                assertThat(item.startTime()).isNotNull();
                assertThat(item.endTime()).isNotNull();
            });
        }

        @Test
        @DisplayName("스케줄링이 실패해도 예외 없이 AI 순서 그대로 미리보기를 반환한다")
        void schedulingFails_fallsBackToAiOrder() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            try (MockedStatic<ItineraryPlanner> planner = mockStatic(ItineraryPlanner.class)) {
                planner.when(() -> ItineraryPlanner.plan(any(), any(), any(), any(), any()))
                        .thenThrow(new IllegalStateException("스케줄링 붕괴"));

                // when
                ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

                // then
                assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
                assertThat(response.days().get(0).items())
                        .extracting(ItineraryGenerateResponse.Item::contentId)
                        .containsExactly("c1", "c2");
                assertThat(response.days().get(0).items().get(0).startTime()).isNull();
                assertThat(response.adjustments()).isEmpty();
            }
        }

        @Test
        @DisplayName("AI가 바구니에 없는 장소만 반환하면 해당 일차를 비운다")
        void unknownContentIds_areFilteredOut() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "지어낸 일정",
                    List.of(new AiDay(1, List.of(new AiItem("없는-장소", 1, "환각"))))
            ));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).isEmpty();
        }

        @Test
        @DisplayName("콘텐츠 상세 조회가 실패해도 바구니 스냅샷만으로 AI를 호출한다")
        void detailFails_fallsBackToSnapshot() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willThrow(new RuntimeException("TourAPI 장애"));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items()).hasSize(2);

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().places().get(0).latitude()).isNull();
            assertThat(captor.getValue().places().get(0).category()).isEqualTo("12");
        }

        @Test
        @DisplayName("AI 응답에 바구니에 없는 contentId가 섞이면 해당 항목을 제외한다")
        void unknownContentId_isFilteredOut() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "바구니에 있는 장소입니다."),
                            new AiItem("ghost-99", 2, "AI가 지어낸 장소입니다."),
                            new AiItem("c2", 3, "바구니에 있는 장소입니다.")
                    )))
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            // 스케줄러가 순서를 다시 정하므로 AI 원본 번호(1, 3)가 아니라 1부터 다시 매겨진다.
            // 걸러낸 자리에 구멍이 남지 않고, 재정렬이 일어나도 번호가 실제 방문 순서와 일치한다.
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::order)
                    .containsExactly(1, 2);
        }

        @Test
        @DisplayName("일차의 모든 항목이 바구니에 없으면 빈 일차로 남긴다 (구조는 그대로)")
        void allItemsUnknown_keepsEmptyDay() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(
                            new AiDay(1, List.of(new AiItem("c1", 1, "바구니에 있는 장소입니다."))),
                            new AiDay(2, List.of(new AiItem("ghost-1", 1, "지어낸 장소"), new AiItem("ghost-2", 2, "지어낸 장소")))
                    )
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days()).hasSize(2);
            assertThat(response.days().get(0).items()).extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1");
            assertThat(response.days().get(1).items()).isEmpty();
        }

        @Test
        @DisplayName("AI 제공자 호출이 실패하면 예외가 그대로 전파된다")
        void aiFails_propagates() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any()))
                    .willThrow(new ItineraryException(ErrorCode.ITINERARY_PROVIDER_FAILED));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }
    }

    @Nested
    @DisplayName("generate - 생성 모드")
    class Modes {

        private void givenBasketAndDetails(Basket basket) {
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
        }

        private AiItineraryResult resultWithExtra(String extraContentId, String extraReason) {
            return new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "바구니에 있는 장소입니다."),
                            new AiItem(extraContentId, 2, extraReason),
                            new AiItem("c2", 3, "바구니에 있는 장소입니다.")
                    )))
            );
        }

        @Test
        @DisplayName("모드를 지정하지 않으면 STRICT 로 동작해 바구니 밖 장소를 모두 제거한다")
        void defaultRequest_behavesAsStrict() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "지역 콘텐츠입니다."));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, GenerateItineraryRequest.defaults());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::addedByAi)
                    .containsOnly(false);
            verify(travelContentRepository, never()).findIdsByRegion(any(), any(), any());
            verify(travelContentRepository, never()).findRegionCandidates(any(), any(), any(), any());

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates()).isEmpty();
        }

        @Test
        @DisplayName("AUGMENT 면 같은 지역에 적재된 추가 장소를 유지하고 addedByAi 를 true 로 표시한다")
        void augment_keepsRegionContentAndFlagsIt() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            // 후보로 제시한 장소를 AI 가 실제로 골라 오는 AUGMENT 정상 흐름
            given(travelContentRepository.findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), any(), any()))
                    .willReturn(List.of(new RegionContentProjection("x9", "최참판댁", "12")));
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            assertThat(items)
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "x9", "c2");
            assertThat(items)
                    .filteredOn(item -> item.contentId().equals("x9"))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.addedByAi()).isTrue();
                        // 바구니 스냅샷에 없으므로 표시명은 스케줄러가 들고 있던 콘텐츠 상세 값으로 폴백한다.
                        assertThat(item.title()).isEqualTo("title-x9");
                    });
            assertThat(items)
                    .filteredOn(item -> !item.contentId().equals("x9"))
                    .extracting(ItineraryGenerateResponse.Item::addedByAi)
                    .containsOnly(false);

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates())
                    .extracting(AiPlace::contentId)
                    .containsExactly("x9");
        }

        @Test
        @DisplayName("AUGMENT 면 같은 지역 후보를 AI 프롬프트 입력으로 실어 보낸다 (바구니 항목은 제외)")
        void augment_sendsRegionCandidatesToAi() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), any(), any()))
                    .willReturn(List.of(
                            new RegionContentProjection("x9", "최참판댁", "12"),
                            new RegionContentProjection("x10", "화개장터", "14")));

            // when
            itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates())
                    .extracting(AiPlace::contentId, AiPlace::title, AiPlace::category)
                    .containsExactly(tuple("x9", "최참판댁", "12"), tuple("x10", "화개장터", "14"));
            // 후보에 상세는 채우지 않는다 (추가 API 호출 없이 repository 한 번으로 끝낸다).
            assertThat(captor.getValue().extraCandidates())
                    .allSatisfy(candidate -> assertThat(candidate.latitude()).isNull());

            ArgumentCaptor<Collection<String>> excludedCaptor = ArgumentCaptor.forClass(Collection.class);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(travelContentRepository).findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), excludedCaptor.capture(), pageableCaptor.capture());
            assertThat(excludedCaptor.getValue()).containsExactlyInAnyOrder("c1", "c2");
            // 프롬프트 토큰 비용 때문에 후보 수는 상한으로 잘라 조회한다.
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(80);
        }

        @Test
        @DisplayName("AUGMENT 인데 지역 후보가 없으면 후보 없이 정상 생성한다")
        void augment_withoutCandidates_stillGenerates() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(any(), any(), any(), any()))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }

        @Test
        @DisplayName("AUGMENT 후보 조회가 실패해도 예외 없이 후보 없이 생성한다")
        void augment_candidateQueryFails_generatesWithoutCandidates() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(any(), any(), any(), any()))
                    .willThrow(new RuntimeException("DB 장애"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates()).isEmpty();
        }

        @Test
        @DisplayName("AUGMENT 로 추가된 장소도 스케줄링으로 방문 시각을 배정받는다")
        void augment_extraPlaceGetsVisitTimes() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items()).allSatisfy(item -> {
                assertThat(item.startTime()).isNotNull();
                assertThat(item.endTime()).isNotNull();
            });
        }

        @Test
        @DisplayName("AUGMENT 로 추가된 장소의 배치 이유도 내부 값 정제를 거친다")
        void augment_extraPlaceReasonIsSanitized() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(
                    resultWithExtra("x9", "슬로시티(773075)와 가깝고 LESS_WALKING 조건이라 추가했습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            String extraReason = response.days().get(0).items().stream()
                    .filter(item -> item.contentId().equals("x9"))
                    .findFirst()
                    .orElseThrow()
                    .reason();
            assertThat(extraReason)
                    .doesNotContain("773075")
                    .doesNotContain("LESS_WALKING")
                    .contains("슬로시티")
                    .contains("걷기 적게");
        }

        @Test
        @DisplayName("AUGMENT 여도 DB 에 없는 contentId 는 제거한다")
        void augment_removesUnknownContentId() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("ghost-99", "AI가 지어낸 장소입니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }

        @Test
        @DisplayName("AUGMENT 여도 다른 지역 콘텐츠는 제거한다 (조회를 바구니 지역으로 한정한다)")
        void augment_removesOtherRegionContent() {
            // given: 영주 콘텐츠라 하동 지역 조회 결과에 포함되지 않는다
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("yeongju-1", "영주 콘텐츠입니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            verify(travelContentRepository).findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE));
        }

        @Test
        @DisplayName("AUGMENT 에서 추가 장소의 상세 조회가 실패하면 그 장소만 제외하고 일정은 생성한다")
        void augment_detailFailure_dropsOnlyThatPlace() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString())).willAnswer(invocation -> {
                if ("x9".equals(invocation.getArgument(0))) {
                    throw new RuntimeException("TourAPI 장애");
                }
                return detail(invocation.getArgument(0));
            });
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("요청을 일정 엔티티로 변환해 저장하고 응답으로 매핑한다")
        void savesItinerary() {
            given(itineraryRepository.save(any(Itinerary.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            ItineraryResponse response = itineraryService.save(USER_ID, saveRequest());

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.region()).isEqualTo(Region.HADONG);
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
            assertThat(response.days().get(0).items().get(0).pinned()).isTrue();
            assertThat(response.days().get(0).items().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(response.days().get(0).totalTravelMinutes()).isEqualTo(25);
            verify(itineraryRepository).save(any(Itinerary.class));
        }
    }

    @Nested
    @DisplayName("getItinerary")
    class GetItinerary {

        @Test
        @DisplayName("본인 소유 일정이면 상세를 반환한다")
        void ownedItinerary_returnsResponse() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(USER_ID)));

            ItineraryResponse response = itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThat(response.title()).isEqualTo("기존 제목");
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
        }

        @Test
        @DisplayName("일정이 없으면 ITINERARY_NOT_FOUND 예외를 던진다")
        void notFound_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }

        @Test
        @DisplayName("타인 소유 일정이면 존재를 숨기고 ITINERARY_NOT_FOUND 예외를 던진다")
        void notOwned_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("modify")
    class Modify {

        @Test
        @DisplayName("본인 일정의 일차·항목 구성을 통째로 교체한다")
        void replacesStructure() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));

            ItineraryResponse response = itineraryService.modify(USER_ID, ITINERARY_ID, saveRequest());

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).pinned()).isTrue();
            assertThat(itinerary.getDays().get(0).getItems()).hasSize(2);
        }

        @Test
        @DisplayName("타인 소유 일정이면 ITINERARY_NOT_FOUND 예외를 던진다")
        void notOwned_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.modify(USER_ID, ITINERARY_ID, saveRequest());

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("regenerate")
    class Regenerate {

        @Test
        @DisplayName("바구니 기준으로 다시 생성해 기존 일정을 덮어쓴다")
        void overwritesFromBasket() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(basketRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(basketWith(Region.HADONG, 2, "c1", "c2")));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryResponse response = itineraryService.regenerate(USER_ID, ITINERARY_ID);

            assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(1).contentId()).isEqualTo("c2");
        }
    }

    @Nested
    @DisplayName("getMyItineraries")
    class GetMyItineraries {

        @Test
        @DisplayName("로그인 사용자의 일정 목록을 최근 수정순 요약으로 반환한다")
        void returnsSummariesInRepositoryOrder() {
            UUID recentId = UUID.randomUUID();
            UUID olderId = UUID.randomUUID();
            Itinerary recent = mockSummaryItinerary(recentId, "최근 일정", LocalDateTime.of(2026, 7, 10, 0, 0));
            Itinerary older = mockSummaryItinerary(olderId, "이전 일정", LocalDateTime.of(2026, 7, 1, 0, 0));
            given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(USER_ID))
                    .willReturn(List.of(recent, older));

            List<ItinerarySummaryResponse> responses = itineraryService.getMyItineraries(USER_ID);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).itineraryId()).isEqualTo(recentId);
            assertThat(responses.get(0).title()).isEqualTo("최근 일정");
            assertThat(responses.get(0).region()).isEqualTo(Region.HADONG);
            assertThat(responses.get(0).travelDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(responses.get(0).duration()).isEqualTo(2);
            assertThat(responses.get(0).lastModifiedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 0, 0));
            assertThat(responses.get(1).itineraryId()).isEqualTo(olderId);
        }

        @Test
        @DisplayName("저장된 일정이 없으면 빈 목록을 반환한다")
        void noItineraries_returnsEmptyList() {
            given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(USER_ID)).willReturn(List.of());

            List<ItinerarySummaryResponse> responses = itineraryService.getMyItineraries(USER_ID);

            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("본인 일정을 삭제하면 itineraryRepository.delete 가 호출된다")
        void deletesOwnedItinerary() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID)).willReturn(Optional.empty());

            itineraryService.delete(USER_ID, ITINERARY_ID);

            verify(itineraryRepository).delete(itinerary);
        }

        @Test
        @DisplayName("타인 일정 삭제 요청은 ITINERARY_NOT_FOUND 예외를 던지고 delete 가 호출되지 않는다")
        void notOwned_throwsAndDoesNotDelete() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
            verify(itineraryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("일정 삭제 시 활성 공유 토큰이 비활성화된다")
        void deactivatesActiveShareToken() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            ShareToken shareToken = ShareToken.builder().itineraryId(ITINERARY_ID).token("share-token").build();
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID))
                    .willReturn(Optional.of(shareToken));

            itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThat(shareToken.isActive()).isFalse();
            verify(itineraryRepository).delete(itinerary);
        }

        @Test
        @DisplayName("활성 공유 토큰이 없어도 삭제가 정상 동작한다")
        void noActiveShareToken_stillDeletes() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThatCode(action).doesNotThrowAnyException();
            verify(itineraryRepository).delete(itinerary);
        }
    }

    @Nested
    @DisplayName("generate - 혼잡 기반 순서변경 제안")
    class CongestionSuggestions {

        /** 스케줄러가 배정한 시각과 무관하게 검증하려고 모든 시간대에 같은 레벨을 채운다. */
        private Map<String, Map<Integer, CongestionLevel>> allHours(Map<String, CongestionLevel> byContentId) {
            Map<String, Map<Integer, CongestionLevel>> levels = new HashMap<>();
            byContentId.forEach((contentId, level) -> {
                Map<Integer, CongestionLevel> byHour = new HashMap<>();
                for (int hour = 0; hour < 24; hour++) {
                    byHour.put(hour, level);
                }
                levels.put(contentId, byHour);
            });
            return levels;
        }

        private void givenTwoPlaceItinerary() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
        }

        @Test
        @DisplayName("붐비는 장소 뒤에 덜 붐비는 장소가 있으면 순서를 바꾸자고 제안한다")
        void 트리거충족_제안생성() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.HIGH,
                    "c2", CongestionLevel.LOW)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).hasSize(1);
            ItineraryGenerateResponse.Suggestion suggestion = response.suggestions().get(0);
            assertThat(suggestion.type()).isEqualTo("CONGESTION_REORDER");
            assertThat(suggestion.dayIndex()).isEqualTo(1);
            assertThat(suggestion.contentId()).isEqualTo("c1");
            assertThat(suggestion.swapWithContentId()).isEqualTo("c2");
            assertThat(suggestion.message()).contains("title-c1", "title-c2", "붐빕니다");
            // 제안만 하고 실제 순서는 그대로 둔다 (수락은 PATCH /{id} 로 처리한다).
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
        }

        @Test
        @DisplayName("붐비는 장소가 없으면 제안하지 않는다")
        void 트리거미충족_제안없음() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.MEDIUM,
                    "c2", CongestionLevel.LOW)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).isEmpty();
        }

        @Test
        @DisplayName("뒤쪽 장소도 똑같이 붐비면 바꿀 이유가 없어 제안하지 않는다")
        void 대체후보없음_제안없음() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.HIGH,
                    "c2", CongestionLevel.HIGH)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).isEmpty();
        }

        @Test
        @DisplayName("혼잡 조회가 실패해도 일정 생성은 성공하고 제안은 빈 리스트가 된다")
        void 혼잡조회실패_생성성공() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willThrow(new RuntimeException("congestion down"));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.suggestions()).isEmpty();
        }
    }
}
