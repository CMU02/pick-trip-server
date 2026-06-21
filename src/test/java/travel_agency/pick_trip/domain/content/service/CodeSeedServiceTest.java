package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiCodeResponse;
import travel_agency.pick_trip.domain.content.entity.CategoryCode;
import travel_agency.pick_trip.domain.content.repository.CategoryCodeRepository;
import travel_agency.pick_trip.domain.content.repository.LclsSystmCodeRepository;
import travel_agency.pick_trip.domain.region.entity.AreaCode;
import travel_agency.pick_trip.domain.region.repository.AreaCodeRepository;
import travel_agency.pick_trip.domain.region.repository.LdongCodeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeSeedService")
class CodeSeedServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private AreaCodeRepository areaCodeRepository;
    @Mock private LdongCodeRepository ldongCodeRepository;
    @Mock private CategoryCodeRepository categoryCodeRepository;
    @Mock private LclsSystmCodeRepository lclsSystmCodeRepository;

    @InjectMocks private CodeSeedService codeSeedService;

    // --- 테스트 헬퍼 ---

    private TourApiCodeResponse codeResponse(TourApiCodeResponse.Item... items) {
        return new TourApiCodeResponse(
                new TourApiCodeResponse.Response(
                        new TourApiCodeResponse.Body(
                                new TourApiCodeResponse.Items(List.of(items)), items.length, 1, items.length)));
    }

    private TourApiCodeResponse.Item item(String code, String name) {
        return new TourApiCodeResponse.Item("1", code, name);
    }

    /** feign.FeignException 인스턴스 (protected 생성자를 익명 서브클래스로 호출). */
    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("지역 코드 seed는 시도와 시군구를 신규 저장한다")
    void seedAreaCodes_신규코드_저장() {
        given(tourApiClient.getAreaCode(isNull(), eq(1), eq(100)))
                .willReturn(codeResponse(item("35", "경상북도")));
        given(tourApiClient.getAreaCode(eq("35"), eq(1), eq(100)))
                .willReturn(codeResponse(item("14", "영주시")));
        given(areaCodeRepository.findByCodeAndParentCode(any(), any()))
                .willReturn(Optional.empty());

        codeSeedService.seedAreaCodes();

        ArgumentCaptor<AreaCode> captor = ArgumentCaptor.forClass(AreaCode.class);
        verify(areaCodeRepository, times(2)).save(captor.capture());

        AreaCode sido = captor.getAllValues().get(0);
        assertThat(sido.getCode()).isEqualTo("35");
        assertThat(sido.getName()).isEqualTo("경상북도");
        assertThat(sido.getParentCode()).isEmpty();

        AreaCode sigungu = captor.getAllValues().get(1);
        assertThat(sigungu.getCode()).isEqualTo("14");
        assertThat(sigungu.getParentCode()).isEqualTo("35");
    }

    @Test
    @DisplayName("이미 존재하는 코드는 새로 저장하지 않고 이름을 갱신한다")
    void seedAreaCodes_기존코드_갱신() {
        AreaCode existing = AreaCode.builder().code("35").name("옛이름").parentCode("").build();
        given(tourApiClient.getAreaCode(isNull(), eq(1), eq(100)))
                .willReturn(codeResponse(item("35", "경상북도")));
        given(tourApiClient.getAreaCode(eq("35"), eq(1), eq(100)))
                .willReturn(codeResponse());
        given(areaCodeRepository.findByCodeAndParentCode("35", ""))
                .willReturn(Optional.of(existing));

        codeSeedService.seedAreaCodes();

        verify(areaCodeRepository, never()).save(any());
        assertThat(existing.getName()).isEqualTo("경상북도");
    }

    @Test
    @DisplayName("TourAPI 호출 실패 시 예외를 전파하지 않고 저장도 하지 않는다")
    void seedAreaCodes_호출실패_저장없음() {
        given(tourApiClient.getAreaCode(isNull(), eq(1), eq(100)))
                .willThrow(feignError());

        assertThatCode(() -> codeSeedService.seedAreaCodes()).doesNotThrowAnyException();

        verify(areaCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("분류 코드 seed는 대/중/소분류를 depth와 함께 저장한다")
    void seedCategoryCodes_3depth_저장() {
        given(tourApiClient.getCategoryCode(isNull(), isNull(), eq(1), eq(100)))
                .willReturn(codeResponse(item("A01", "자연")));
        given(tourApiClient.getCategoryCode(eq("A01"), isNull(), eq(1), eq(100)))
                .willReturn(codeResponse(item("A0101", "자연관광지")));
        given(tourApiClient.getCategoryCode(eq("A01"), eq("A0101"), eq(1), eq(100)))
                .willReturn(codeResponse(item("A01010100", "국립공원")));
        given(categoryCodeRepository.findByCodeAndParentCode(any(), any()))
                .willReturn(Optional.empty());

        codeSeedService.seedCategoryCodes();

        ArgumentCaptor<CategoryCode> captor = ArgumentCaptor.forClass(CategoryCode.class);
        verify(categoryCodeRepository, times(3)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(CategoryCode::getCode, CategoryCode::getParentCode, CategoryCode::getDepth)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A01", "", 1),
                        org.assertj.core.groups.Tuple.tuple("A0101", "A01", 2),
                        org.assertj.core.groups.Tuple.tuple("A01010100", "A0101", 3));
    }
}
