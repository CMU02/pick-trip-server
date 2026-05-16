package travel_agency.pick_trip.gloal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(String baseUrl, String serviceKey) {}
