package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailCommonResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

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
            String homepage,
            String mapx,
            String mapy,
            String firstimage,
            String overview
    ) {}
}
