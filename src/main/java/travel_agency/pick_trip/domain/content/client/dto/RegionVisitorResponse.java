package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 공공데이터포털 "한국관광공사 빅데이터 지역별 방문자수"(15101972) 의
 * 기초지자체 일자별 방문자수({@code /locgoRegnVisitrDDList}) 응답.
 *
 * <p>TourAPI 와 같은 {@code response.header/body} 구조라 {@link TourApiResponse} 계약을 그대로 쓴다.
 * {@code touNum}(방문자수)은 원천이 소수로 내려주는 경우가 있어 {@link Double} 로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegionVisitorResponse(Response response) implements TourApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String baseYmd,
            String areaCd,
            String signguCd,
            String signguNm,
            /** 관광객 구분 코드. 1=현지인, 2=외지인, 3=외국인. */
            String touDivCd,
            String touDivNm,
            Double touNum
    ) {}

    /** 응답이 비어 있어도 안전하게 빈 목록을 반환한다. */
    public List<Item> items() {
        if (response == null
                || response.body() == null
                || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }

    @Override
    public String resultCode() {
        return response != null && response.header() != null ? response.header().resultCode() : null;
    }

    @Override
    public String resultMsg() {
        return response != null && response.header() != null ? response.header().resultMsg() : null;
    }
}
