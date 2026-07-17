package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link OAuthExchangeCodeStore} 의 인메모리 TTL 구현.
 *
 * <p>단일 인스턴스 전용이다. 스케일 아웃 시에는 Redis 등 공유 저장소 구현으로 교체한다.</p>
 */
@Component
public class InMemoryOAuthExchangeCodeStore implements OAuthExchangeCodeStore {

    // 128비트(16바이트) 이상의 CSPRNG 난수를 URL-safe Base64 로 인코딩해 추측을 불가능하게 한다.
    private static final int CODE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

    // 교환 코드 수명(초). 콜백 직후 프론트가 곧바로 교환하므로 짧게 유지한다.
    private final Duration ttl;

    public InMemoryOAuthExchangeCodeStore(
            @Value("${app.oauth2.exchange-code-ttl-seconds:60}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public String issue(OAuthExchangeData data) {
        purgeExpired();
        String code = generateCode();
        store.put(code, new Entry(data, Instant.now().plus(ttl)));
        return code;
    }

    @Override
    public Optional<OAuthExchangeData> consume(String code) {
        if (code == null) {
            return Optional.empty();
        }
        // 회수 즉시 제거해 재사용을 원천 차단한다(1회성).
        Entry entry = store.remove(code);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.data());
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    // issue 시점마다 만료 항목을 정리해 저장소가 무한히 커지지 않도록 한다.
    private void purgeExpired() {
        store.values().removeIf(Entry::isExpired);
    }

    private record Entry(OAuthExchangeData data, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
