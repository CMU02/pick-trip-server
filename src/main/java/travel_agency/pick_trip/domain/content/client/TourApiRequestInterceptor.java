package travel_agency.pick_trip.domain.content.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.gloal.config.TourApiProperties;

@Component
@RequiredArgsConstructor
public class TourApiRequestInterceptor implements RequestInterceptor {

    private final TourApiProperties tourApiProperties;

    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", tourApiProperties.serviceKey());
        template.query("_type", "json");
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
    }
}
