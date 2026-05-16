package travel_agency.pick_trip.domain.content.dto.response;

import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;

public record ContentSummaryResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String firstImage,
        double latitude,
        double longitude
) {
    public static ContentSummaryResponse from(TourApiListResponse.Item item) {
        return new ContentSummaryResponse(
                item.contentid(),
                item.title(),
                parseIntOrZero(item.contenttypeid()),
                buildAddress(item.addr1(), item.addr2()),
                item.firstimage(),
                parseDouble(item.mapy()),
                parseDouble(item.mapx())
        );
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private static String buildAddress(String addr1, String addr2) {
        if (addr2 == null || addr2.isBlank()) return addr1;
        return addr1 + " " + addr2;
    }
}
