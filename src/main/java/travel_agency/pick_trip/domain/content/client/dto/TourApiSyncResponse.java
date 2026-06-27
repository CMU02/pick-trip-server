package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TourAPI {@code /areaBasedSyncList2} 동기화 응답. {@code showflag}로 노출 여부(1=노출, 0=비노출),
 * {@code modifiedtime}으로 변경 시점을 판단한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiSyncResponse(Response response) implements TourApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
        /** 헤더 없는 과거 호환 생성자 (테스트·내부 생성용). */
        public Response(Body body) {
            this(null, body);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String modifiedtime,
            String showflag
    ) {}

    /** 응답이 비어 있어도 안전하게 빈 목록을 반환한다. */
    public List<Item> changes() {
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
