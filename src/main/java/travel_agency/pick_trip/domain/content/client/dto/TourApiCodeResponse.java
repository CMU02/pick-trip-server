package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TourAPI 코드 조회 응답({@code /areaCode2}, {@code /ldongCode2}, {@code /categoryCode2}, {@code /lclsSystmCode2}).
 * 네 엔드포인트 모두 {@code response.body.items.item[]}에 {@code code}/{@code name}을 담는 공통 형태를 사용한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiCodeResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String rnum, String code, String name) {}

    /** 응답이 비어 있어도 안전하게 빈 목록을 반환한다. */
    public List<Item> codeItems() {
        if (response == null
                || response.body() == null
                || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }
}
