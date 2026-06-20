package travel_agency.pick_trip.domain.basket.entity;

/**
 * 바구니에 담은 콘텐츠의 방문 우선순위.
 * AI 일정 생성 시 방문 순서·포함 여부 결정의 입력값으로 사용된다.
 */
public enum Priority {
    MUST_VISIT,   // 꼭 가기
    PREFERRED,    // 가면 좋음
    OPTIONAL      // 시간 남으면
}
