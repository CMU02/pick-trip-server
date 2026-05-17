package travel_agency.pick_trip.domain.content.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class TourApiRequestInterceptor implements RequestInterceptor {

    private final String serviceKey;

    public TourApiRequestInterceptor(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", serviceKey);
        template.query("_type", "json");
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
    }
}
