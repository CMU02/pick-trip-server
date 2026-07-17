package travel_agency.pick_trip.domain.auth.oauth2.exchange;

/**
 * 개시 브라우저 바인딩용 nonce 를 OAuth {@code state} 로 키잉해 임시 보관하는 저장소.
 *
 * <p>로그인 시작(<code>/oauth2/authorization/{provider}?nonce=...</code>) 시점에 프론트가 넘긴
 * nonce 를, 그 요청에서 생성된 {@code state} 에 묶어 저장한다. 소셜 콜백으로 돌아온 성공 핸들러가
 * 콜백의 {@code state} 로 nonce 를 회수해 교환 코드에 바인딩한다.</p>
 *
 * <p>이렇게 하면 "이 콜백이 이 브라우저가 시작한 그 로그인의 결과"임을 교환 시점에 검증할 수 있어
 * 로그인 CSRF/세션 고정을 차단한다. nonce 자체는 콜백 URL 에 실리지 않으므로(공격자가 콜백 링크를
 * 알아도 nonce 는 모른다) 바인딩이 유지된다.</p>
 *
 * <p>구현은 인메모리 TTL(단일 인스턴스 전용)이며, 확장 시 Redis 등으로 교체한다.</p>
 */
public interface OAuthNonceStore {

    /** 로그인 시작 시 {@code state → nonce} 매핑을 저장한다. */
    void store(String state, String nonce);

    /**
     * 성공 콜백에서 {@code state} 로 nonce 를 1회 회수한다.
     *
     * @return 저장된 nonce, 없거나 만료면 null
     */
    String consume(String state);
}
