package travel_agency.pick_trip.domain.content.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.basket.repository.projection.BasketContentCountProjection;
import travel_agency.pick_trip.domain.content.client.VisitorStatsClient;
import travel_agency.pick_trip.domain.content.client.dto.RegionVisitorResponse;
import travel_agency.pick_trip.domain.content.dto.response.VisitorStatsResponse;
import travel_agency.pick_trip.domain.content.entity.RegionVisitorStats;
import travel_agency.pick_trip.domain.content.repository.RegionVisitorStatsRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 지역 방문자수 수집과 콘텐츠 응답용 {@link VisitorStatsResponse} 조립 (#73).
 *
 * <p>개별 장소 단위 관광객수를 주는 공개 데이터가 없어 두 단계로 폴백한다.
 * <ol>
 *   <li>지역(시군구) 단위 방문자수 통계 — 수집 배치가 적재한 {@code region_visitor_stats}</li>
 *   <li>자체 프록시 — 그 콘텐츠가 바구니에 담긴 횟수</li>
 * </ol>
 * 둘 다 없으면 {@code null} 을 돌려 응답 필드를 비운다. 어느 경우든 근사값이므로
 * {@code approximate} 는 항상 {@code true} 다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorStatsService {

    /** 응답에 합산할 최근 개월 수. 계절 편차를 덮으면서 기간 표기가 길어지지 않는 선. */
    static final int STATS_MONTHS = 6;
    static final String SOURCE_PUBLIC_DATA = "한국관광공사 지역별 방문자수";
    static final String SOURCE_PROXY = "PickTrip 내부 지표";

    // ponytail: 한 달치(최대 31일 × 관광객 구분 3종 ≒ 93행)를 1페이지로 받고 페이지네이션은 두지 않는다.
    // 원천이 행 구분을 더 잘게 쪼개 500행을 넘기면 그때 totalCount 기준 페이지 순회를 넣는다.
    private static final int COLLECT_PAGE_SIZE = 500;
    /** 관광객 구분 코드 1 = 현지인. 관광객수로 보기 어려워 합산에서 제외한다. */
    private static final String TOUR_DIV_LOCAL_RESIDENT = "1";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final VisitorStatsClient visitorStatsClient;
    private final RegionVisitorStatsRepository regionVisitorStatsRepository;
    private final BasketRepository basketRepository;

    /**
     * 지역의 한 달치 방문자수를 수집해 (지역, 연월) 단위로 upsert 한다. 반환값은 저장한 방문자수.
     *
     * <p>인증키 미승인·한도 초과·장애 어느 경우든 예외를 던지지 않고 0 을 반환한다. 관광객수 노출은
     * 보조 기능이라 수집 실패가 다른 배치 단계나 콘텐츠 조회를 막아서는 안 된다.
     */
    @Transactional
    public long collectRegion(Region region, YearMonth month) {
        RegionVisitorResponse response;
        try {
            response = visitorStatsClient.getLocalRegionVisitors(
                    month.atDay(1).format(YMD),
                    month.atEndOfMonth().format(YMD),
                    region.getLDongRegnCd(),
                    region.getLDongRegnCd() + region.getLDongSignguCd(),
                    1,
                    COLLECT_PAGE_SIZE
            );
        } catch (RuntimeException e) {
            log.warn("[방문자수] {} {} 조회 실패 - 건너뜀: {}", region, month, e.getMessage());
            return 0L;
        }
        if (response == null || response.isError()) {
            log.warn("[방문자수] {} {} 오류 응답 code={} msg={} - 건너뜀", region, month,
                    response == null ? null : response.resultCode(),
                    response == null ? null : response.resultMsg());
            return 0L;
        }

        long visitorCount = response.items().stream()
                .filter(item -> !TOUR_DIV_LOCAL_RESIDENT.equals(item.touDivCd()))
                .filter(item -> item.touNum() != null)
                .mapToLong(item -> Math.round(item.touNum()))
                .sum();
        if (visitorCount <= 0) {
            log.warn("[방문자수] {} {} 집계값이 없어 저장하지 않습니다.", region, month);
            return 0L;
        }

        String statMonth = month.format(STAT_MONTH);
        LocalDateTime now = LocalDateTime.now();
        regionVisitorStatsRepository.findByRegionAndStatMonth(region, statMonth)
                .ifPresentOrElse(
                        stats -> stats.updateCount(visitorCount, now),
                        () -> regionVisitorStatsRepository.save(RegionVisitorStats.builder()
                                .region(region)
                                .statMonth(statMonth)
                                .visitorCount(visitorCount)
                                .source(SOURCE_PUBLIC_DATA)
                                .collectedAt(now)
                                .build()));
        log.info("[방문자수] {} {} {}명 반영", region, statMonth, visitorCount);
        return visitorCount;
    }

    /**
     * 콘텐츠별 관광객수 지표를 조립한다. 지표를 만들 수 없는 콘텐츠는 결과 맵에서 빠지며,
     * 호출 측은 그 콘텐츠의 {@code visitorStats} 를 {@code null} 로 응답한다.
     *
     * <p>지역 통계가 있으면 같은 지역의 모든 콘텐츠가 같은 값을 공유한다(지역 단위 통계라 그렇다).
     */
    @Transactional(readOnly = true)
    public Map<String, VisitorStatsResponse> findByContentIds(Region region, Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        VisitorStatsResponse regionStats = region == null ? null : toRegionStats(region);
        Map<String, VisitorStatsResponse> result = new HashMap<>();
        if (regionStats != null) {
            contentIds.forEach(contentId -> result.put(contentId, regionStats));
            return result;
        }

        LocalDate today = LocalDate.now();
        for (BasketContentCountProjection row : basketRepository.countByContentIds(contentIds)) {
            if (row.getBasketCount() > 0) {
                result.put(row.getContentId(), new VisitorStatsResponse(
                        row.getBasketCount(), null, null, SOURCE_PROXY, today, true));
            }
        }
        return result;
    }

    /** 최근 {@value #STATS_MONTHS} 개월 통계를 누적·일평균으로 요약한다. 적재된 통계가 없으면 null. */
    private VisitorStatsResponse toRegionStats(Region region) {
        List<RegionVisitorStats> recent = regionVisitorStatsRepository
                .findByRegionOrderByStatMonthDesc(region).stream()
                .limit(STATS_MONTHS)
                .toList();
        if (recent.isEmpty()) {
            return null;
        }

        long total = recent.stream().mapToLong(RegionVisitorStats::getVisitorCount).sum();
        YearMonth latest = YearMonth.parse(recent.get(0).getStatMonth());
        YearMonth oldest = YearMonth.parse(recent.get(recent.size() - 1).getStatMonth());
        LocalDate baseDate = latest.atEndOfMonth();
        long days = ChronoUnit.DAYS.between(oldest.atDay(1), baseDate) + 1;

        return new VisitorStatsResponse(
                total,
                days > 0 ? total / days : null,
                oldest + "~" + latest,
                SOURCE_PUBLIC_DATA,
                baseDate,
                true
        );
    }
}
