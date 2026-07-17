package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryOAuthExchangeCodeStore")
class InMemoryOAuthExchangeCodeStoreTest {

    private OAuthExchangeData sampleData() {
        return new OAuthExchangeData(UUID.randomUUID(), "access", "refresh", "nonce");
    }

    @Nested
    @DisplayName("코드 발급")
    class Issue {

        @Test
        @DisplayName("발급된 코드는 비어있지 않고 충분히 길어 추측이 어렵다")
        void issue_returnsUnguessableCode() {
            // given
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(60);

            // when
            String code = store.issue(sampleData());

            // then: 32바이트 난수를 URL-safe Base64 로 인코딩하면 40자 이상이다.
            assertThat(code).isNotBlank();
            assertThat(code.length()).isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("발급할 때마다 서로 다른 코드가 나온다")
        void issue_returnsDistinctCodes() {
            // given
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(60);

            // when
            String first = store.issue(sampleData());
            String second = store.issue(sampleData());

            // then
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("코드 회수")
    class Consume {

        @Test
        @DisplayName("유효한 코드는 바인딩된 데이터를 돌려준다")
        void consume_validCode_returnsData() {
            // given
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(60);
            OAuthExchangeData data = sampleData();
            String code = store.issue(data);

            // when
            Optional<OAuthExchangeData> result = store.consume(code);

            // then
            assertThat(result).contains(data);
        }

        @Test
        @DisplayName("코드는 1회성이다 - 두 번째 회수는 빈 값을 돌려준다")
        void consume_isSingleUse() {
            // given
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(60);
            String code = store.issue(sampleData());

            // when
            store.consume(code);
            Optional<OAuthExchangeData> second = store.consume(code);

            // then
            assertThat(second).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 코드는 빈 값을 돌려준다")
        void consume_unknownCode_returnsEmpty() {
            // given
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(60);

            // when / then
            assertThat(store.consume("no-such-code")).isEmpty();
            assertThat(store.consume(null)).isEmpty();
        }

        @Test
        @DisplayName("만료된 코드는 빈 값을 돌려준다")
        void consume_expiredCode_returnsEmpty() {
            // given: TTL 음수 → 발급 시점에 이미 만료된 상태
            InMemoryOAuthExchangeCodeStore store = new InMemoryOAuthExchangeCodeStore(-1);
            String code = store.issue(sampleData());

            // when / then
            assertThat(store.consume(code)).isEmpty();
        }
    }
}
