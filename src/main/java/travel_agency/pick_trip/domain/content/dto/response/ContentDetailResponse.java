package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;

public record ContentDetailResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String tel,
        String homepage,
        double latitude,
        double longitude,
        String summary,
        String useTime,
        String restDate,
        String parking,
        String useFee,
        String chkBabyCarriage,
        String chkPet,
        String stayDuration,
        Boolean reservationRequired,
        String dataSource,
        List<ImageItem> images
) {
    public record ImageItem(String imageUrl, String title) {}
}
