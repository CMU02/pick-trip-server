package travel_agency.pick_trip.gloal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 외부 API 호출과 DB 트랜잭션을 분리하기 위한 {@link TransactionTemplate} 빈.
 * 수집·동기화 배치가 외부 호출은 트랜잭션 밖에서 수행하고, 영속화만 짧은 트랜잭션으로 감싸도록 한다.
 * (DB 커넥션을 외부 응답 대기 동안 점유하지 않게 해 커넥션 풀 고갈을 방지)
 */
@Configuration
public class PersistenceConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
