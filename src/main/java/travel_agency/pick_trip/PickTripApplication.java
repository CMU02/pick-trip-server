package travel_agency.pick_trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PickTripApplication {

    public static void main(String[] args) {
        SpringApplication.run(PickTripApplication.class, args);
    }

}
