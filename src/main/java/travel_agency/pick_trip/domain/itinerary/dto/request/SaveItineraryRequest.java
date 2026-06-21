package travel_agency.pick_trip.domain.itinerary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 생성된(또는 수정된) 일정을 저장하는 요청.
 * 클라이언트는 미리보기 결과를 그대로, 혹은 사용자가 편집한 상태로 전달한다.
 */
public record SaveItineraryRequest(
        @NotBlank String title,
        @NotNull Region region,
        LocalDate travelDate,
        @NotNull Integer duration,
        @NotEmpty @Valid List<DayRequest> days
) {

    public record DayRequest(
            @NotNull Integer dayIndex,
            @NotEmpty @Valid List<ItemRequest> items
    ) {
    }

    public record ItemRequest(
            @NotBlank String contentId,
            String title,
            int order,
            String reason,
            boolean pinned
    ) {
    }
}
