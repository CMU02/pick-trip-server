package travel_agency.pick_trip.gloal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화. 운영 프로파일({@code prod})에서만 {@code @EnableScheduling}을 켜서
 * 개발·테스트 환경에서 {@code @Scheduled} 배치가 동작하지 않게 한다.
 */
@Configuration
@EnableScheduling
@Profile("prod")
public class SchedulingConfig {
}
