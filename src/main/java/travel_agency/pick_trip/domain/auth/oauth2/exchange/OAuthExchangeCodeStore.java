package travel_agency.pick_trip.domain.auth.oauth2.exchange;

import java.util.Optional;

/**
 * 일회용 OAuth 교환 코드 저장소.
 *
 * <p>OAuth 성공 핸들러가 {@link #issue(OAuthExchangeData)} 로 단명·단추측불가한 code 를 발급하고,
 * 교환 엔드포인트가 {@link #consume(String)} 으로 1회만 회수한다. 만료되었거나 이미 회수된 code 는
 * 빈 값을 돌려준다(재사용/만료 시 401 로 이어진다).</p>
 *
 * <p>구현은 인메모리 TTL 캐시(단일 인스턴스 전용)다. 다중 인스턴스로 확장할 때는 Redis 등
 * 공유 저장소 구현으로 교체한다.</p>
 */
public interface OAuthExchangeCodeStore {

    /**
     * 로그인 결과를 저장하고 opaque 교환 코드를 발급한다.
     *
     * @return URL-safe 한 단추측불가 난수 코드
     */
    String issue(OAuthExchangeData data);

    /**
     * 교환 코드를 1회 회수한다. 회수에 성공하면 즉시 저장소에서 제거되어 재사용이 불가능하다.
     *
     * @return 유효한 코드면 바인딩된 데이터, 없거나 만료·재사용이면 {@link Optional#empty()}
     */
    Optional<OAuthExchangeData> consume(String code);
}
