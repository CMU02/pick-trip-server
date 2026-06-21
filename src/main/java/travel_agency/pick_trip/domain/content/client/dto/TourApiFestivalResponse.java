package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TourAPI {@code /searchFestival2} 축제 검색 응답. 목록 항목에 행사 기간
 * ({@code eventstartdate}/{@code eventenddate})이 포함된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiFestivalResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String tel,
            String mapx,
            String mapy,
            String firstimage,
            String eventstartdate,
            String eventenddate
    ) {}

    /** 응답이 비어 있어도 안전하게 빈 목록을 반환한다. */
    public List<Item> festivals() {
        if (response == null
                || response.body() == null
                || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }
}
