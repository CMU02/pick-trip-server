package travel_agency.pick_trip.domain.content.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TourApiRequestInterceptor implements RequestInterceptor {

    @Value("${tour-api.service-key}")
    private String serviceKey;

    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", serviceKey);
        template.query("_type", "json");
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
    }
}
