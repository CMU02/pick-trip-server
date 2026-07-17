package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link OAuthNonceStore} 의 인메모리 TTL 구현.
 *
 * <p>단일 인스턴스 전용이다. 스케일 아웃 시에는 Redis 등 공유 저장소 구현으로 교체한다.
 * TTL 은 사용자가 소셜 로그인을 완료하기까지의 왕복 시간을 감안해 인가요청 쿠키 수명과 비슷하게 둔다.</p>
 */
@Component
public class InMemoryOAuthNonceStore implements OAuthNonceStore {

    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryOAuthNonceStore(
            @Value("${app.oauth2.nonce-ttl-seconds:300}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void store(String state, String nonce) {
        if (state == null || nonce == null) {
            return;
        }
        purgeExpired();
        store.put(state, new Entry(nonce, Instant.now().plus(ttl)));
    }

    @Override
    public String consume(String state) {
        if (state == null) {
            return null;
        }
        Entry entry = store.remove(state);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.nonce();
    }

    private void purgeExpired() {
        store.values().removeIf(Entry::isExpired);
    }

    private record Entry(String nonce, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
