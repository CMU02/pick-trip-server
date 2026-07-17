package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryOAuthNonceStore")
class InMemoryOAuthNonceStoreTest {

    private static final String STATE = "state-value";
    private static final String NONCE = "browser-nonce";

    @Test
    @DisplayName("state로 저장한 nonce를 회수한다")
    void store_thenConsume_returnsNonce() {
        // given
        InMemoryOAuthNonceStore store = new InMemoryOAuthNonceStore(300);
        store.store(STATE, NONCE);

        // when / then
        assertThat(store.consume(STATE)).isEqualTo(NONCE);
    }

    @Test
    @DisplayName("nonce는 1회성이다 - 두 번째 회수는 null이다")
    void consume_isSingleUse() {
        // given
        InMemoryOAuthNonceStore store = new InMemoryOAuthNonceStore(300);
        store.store(STATE, NONCE);

        // when
        store.consume(STATE);

        // then
        assertThat(store.consume(STATE)).isNull();
    }

    @Test
    @DisplayName("저장되지 않은 state는 null을 돌려준다")
    void consume_unknownState_returnsNull() {
        // given
        InMemoryOAuthNonceStore store = new InMemoryOAuthNonceStore(300);

        // when / then
        assertThat(store.consume("unknown")).isNull();
        assertThat(store.consume(null)).isNull();
    }

    @Test
    @DisplayName("만료된 nonce는 null을 돌려준다")
    void consume_expiredNonce_returnsNull() {
        // given: TTL 음수 → 저장 시점에 이미 만료
        InMemoryOAuthNonceStore store = new InMemoryOAuthNonceStore(-1);
        store.store(STATE, NONCE);

        // when / then
        assertThat(store.consume(STATE)).isNull();
    }

    @Test
    @DisplayName("state나 nonce가 null이면 저장하지 않는다")
    void store_ignoresNullArguments() {
        // given
        InMemoryOAuthNonceStore store = new InMemoryOAuthNonceStore(300);

        // when
        store.store(null, NONCE);
        store.store(STATE, null);

        // then
        assertThat(store.consume(STATE)).isNull();
    }
}
