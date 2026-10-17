package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 가 제안한 일차별 장소 배열을 실제 여행 가능한 일정으로 확정한다.
 * 바구니 화이트리스트 검증 → 좌표 기반 일차 재배분 → 휴무일 재배치 → 일차별 시각 배정 순으로 처리한다.
 * AI 가 나눈 일차 배분은 어느 경우에도 초기값일 뿐이며, 실제 배분·순서는 서버가 좌표로 다시 정한다.
 * 휴무일 재배치를 재배분보다 뒤에 두는 이유는, 재배분이 휴무일을 모르는 채 장소를 옮기기 때문이다.
 */
public final class ItineraryPlanner {

    private static final String NO_TRAVEL_DATE_MESSAGE = "여행 날짜가 없어 휴무일 검증을 건너뛰었습니다.";
    private static final String CLOSED_STOP_NOTE = "방문 예정일이 휴무일입니다.";

    /** DayOfWeek.getDisplayName 은 JVM locale provider 설정에 좌우되므로 직접 고정한다. */
    private static final String[] KOREAN_DAY_NAMES = {
            "", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"
    };

    private ItineraryPlanner() {
    }

    /**
     * @param context 이동수단·도로 행렬·시작 지점. 이동수단에 따라 구간 소요 시간 모델이 달라져
     *                같은 장소 집합이라도 일차 배분·방문 순서가 달라진다.
     *                시작 지점을 지정하면 그 장소에서 경로를 시작해 해당 일차의 첫 스톱으로 고정하고,
     *                null 이면 좌표를 가진 장소마다 출발점으로 삼아 보고 총 이동시간이 가장 짧은 안을 고르며,
     *                첫 자리도 재배치 대상이 된다.
     */
    public static PlannedItinerary plan(String title,
                                        List<List<String>> dayContentIds,
                                        Map<String, SchedulingPlace> placesById,
                                        Map<String, String> reasonByContentId,
                                        LocalDate travelDate,
                                        SchedulingContext context) {
        List<List<String>> requested = (dayContentIds == null) ? List.of() : dayContentIds;
        Map<String, SchedulingPlace> places = (placesById == null) ? Map.of() : placesById;
        Map<String, String> reasons = (reasonByContentId == null) ? Map.of() : reasonByContentId;

        List<List<String>> filtered = filterToBasket(requested, places);
        if (context.startContentId() != null) {
            return planFrom(title, filtered, places, reasons, travelDate, context);
        }

        // 시작 지점이 없으면 재배분은 목록의 첫 좌표 장소, 즉 AI 가 처음 적은 장소에서 출발한다.
        // 그 출발점 하나에 따라 같은 바구니의 이동시간이 최적 대비 +2%~+36% 로 갈려(#97 측정),
        // 좌표를 가진 장소마다 출발점으로 삼아 계획해 보고 총 이동시간이 가장 짧은 안을 고른다.
        // 동률이면 먼저 시도한 AI 첫 장소 안이 남는다.
        // shortcut: 이동시간만 비교하고 운영시간 위반 수는 보지 않는다. 위반이 늘어난 안이 뽑힌다는 제보가 오면 비교 기준에 더한다.
        PlannedItinerary best = null;
        int bestMinutes = Integer.MAX_VALUE;
        for (String start : coordinateIdsInOrder(filtered, places)) {
            PlannedItinerary candidate = planFrom(title, startingWith(filtered, start), places, reasons, travelDate,
                    context);
            int minutes = routeMinutes(candidate, places, context);
            if (minutes < bestMinutes) {
                best = candidate;
                bestMinutes = minutes;
            }
        }
        return best != null ? best : planFrom(title, filtered, places, reasons, travelDate, context);
    }

    /** 재배분 → 휴무일 재배치 → 일차별 시각 배정. 재배분은 목록의 첫 좌표 장소(또는 지정된 시작 지점)에서 출발한다. */
    private static PlannedItinerary planFrom(String title,
                                             List<List<String>> filtered,
                                             Map<String, SchedulingPlace> places,
                                             Map<String, String> reasons,
                                             LocalDate travelDate,
                                             SchedulingContext context) {
        List<String> adjustments = new ArrayList<>();
        List<List<String>> daysPlan = RouteOptimizer.redistribute(filtered, places, context);

        // 휴무일이지만 옮길 일차가 없어 그대로 둔 장소. 스케줄링 후 안내를 덧붙이기 위해 모아둔다.
        Set<String> stillClosed = new HashSet<>();
        if (travelDate == null) {
            adjustments.add(NO_TRAVEL_DATE_MESSAGE);
        } else {
            moveClosedStops(daysPlan, places, travelDate, adjustments, stillClosed);
        }

        List<ScheduledDay> days = new ArrayList<>(daysPlan.size());
        for (int i = 0; i < daysPlan.size(); i++) {
            List<SchedulingPlace> dayPlaces = daysPlan.get(i).stream().map(places::get).toList();
            ScheduledDay day = DayScheduler.schedule(
                    i + 1,
                    travelDate == null ? null : travelDate.plusDays(i),
                    dayPlaces,
                    reasons,
                    context);
            days.add(stillClosed.isEmpty() ? day : withClosedNotes(day, stillClosed));
        }

        return new PlannedItinerary(title, days, adjustments);
    }

    /** 좌표를 가진 장소 id 를 입력 순서대로. 재배분이 출발점으로 삼을 수 있는 후보다. */
    private static List<String> coordinateIdsInOrder(List<List<String>> daysPlan, Map<String, SchedulingPlace> places) {
        return daysPlan.stream()
                .flatMap(List::stream)
                .filter(contentId -> places.get(contentId).hasCoordinates())
                .toList();
    }

    /** {@code start} 를 1일차 맨 앞으로 옮긴 사본. 재배분은 목록을 펼친 순서에서 첫 좌표 장소를 출발점으로 삼는다. */
    private static List<List<String>> startingWith(List<List<String>> daysPlan, String start) {
        List<List<String>> result = new ArrayList<>(daysPlan.size());
        for (List<String> day : daysPlan) {
            result.add(day.stream().filter(contentId -> !contentId.equals(start))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        result.get(0).add(0, start);
        return result;
    }

    /**
     * 하루 안 이동시간에 전날 마지막 장소 → 다음 날 첫 장소 이동을 더한 값.
     * 일정은 다음 날이 전날 마지막 장소 근처에서 시작한다고 보므로, 날짜를 넘어가는 이동을 빼고 비교하면
     * 하루 안은 짧아도 밤사이 크게 되돌아가는 안이 뽑힌다(#97 측정: 넣으면 총 이동이 3~5% 더 준다).
     * 숙소 위치는 입력받지 않아 숙소 왕복은 들어가지 않는다.
     */
    private static int routeMinutes(PlannedItinerary itinerary, Map<String, SchedulingPlace> places,
                                    SchedulingContext context) {
        int minutes = 0;
        SchedulingPlace previousLast = null;
        for (ScheduledDay day : itinerary.days()) {
            minutes += day.totalTravelMinutes();
            if (day.stops().isEmpty()) {
                continue;
            }
            if (previousLast != null) {
                TravelMatrix.Leg overnight = context.legBetween(previousLast, places.get(day.stops().get(0).contentId()));
                // DayScheduler 와 같게, 좌표가 없어 잴 수 없는 구간은 고정 추정치를 쓴다.
                minutes += (overnight == null) ? SchedulingPolicy.UNKNOWN_HOP_MINUTES : overnight.minutes();
            }
            previousLast = places.get(day.stops().get(day.stops().size() - 1).contentId());
        }
        return minutes;
    }

    /**
     * 바구니(placesById)에 없는 contentId 를 걷어낸다. AI 가 지어낸 장소와 중복 배치를 여기서 모두 제거한다.
     * 일차 수는 그대로 유지해 사용자가 요청한 여행 길이를 바꾸지 않는다.
     */
    private static List<List<String>> filterToBasket(List<List<String>> requested,
                                                     Map<String, SchedulingPlace> places) {
        Set<String> used = new HashSet<>();
        List<List<String>> result = new ArrayList<>(requested.size());
        for (List<String> day : requested) {
            List<String> kept = new ArrayList<>();
            if (day != null) {
                for (String contentId : day) {
                    // containsKey 가 아니라 get 으로 값까지 확인한다. 값이 null 인 엔트리를 통과시키면
                    // 뒤의 휴무일 패스에서 NPE 가 난다.
                    if (contentId != null && places.get(contentId) != null && used.add(contentId)) {
                        kept.add(contentId);
                    }
                }
            }
            result.add(kept);
        }
        return result;
    }

    private static void moveClosedStops(List<List<String>> daysPlan,
                                        Map<String, SchedulingPlace> places,
                                        LocalDate travelDate,
                                        List<String> adjustments,
                                        Set<String> stillClosed) {
        for (int day = 0; day < daysPlan.size(); day++) {
            DayOfWeek dayOfWeek = travelDate.plusDays(day).getDayOfWeek();
            // 순회 중 원본 리스트에서 제거하므로 복사본을 돈다.
            for (String contentId : List.copyOf(daysPlan.get(day))) {
                SchedulingPlace place = places.get(contentId);
                if (!place.operatingHours().isClosedOn(dayOfWeek)) {
                    continue;
                }
                int target = findOpenDay(daysPlan.size(), day, travelDate, place);
                if (target < 0) {
                    stillClosed.add(contentId);
                    continue;
                }
                daysPlan.get(day).remove(contentId);
                daysPlan.get(target).add(contentId);
                adjustments.add("'%s'은 %d일차(%s)에 휴무여서 %d일차로 옮겼습니다."
                        .formatted(place.title(), day + 1, KOREAN_DAY_NAMES[dayOfWeek.getValue()], target + 1));
            }
        }
    }

    /** 현재 일차에서 가장 가까운(동점이면 앞선) 영업일 인덱스. 없으면 -1. */
    private static int findOpenDay(int dayCount, int from, LocalDate travelDate, SchedulingPlace place) {
        for (int distance = 1; distance < dayCount; distance++) {
            for (int candidate : new int[]{from - distance, from + distance}) {
                if (candidate < 0 || candidate >= dayCount) {
                    continue;
                }
                if (!place.operatingHours().isClosedOn(travelDate.plusDays(candidate).getDayOfWeek())) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    /**
     * DayScheduler 는 휴무 여부를 알지 못하므로, 결과를 받은 뒤 해당 스톱에만 안내를 덧붙인다.
     */
    private static ScheduledDay withClosedNotes(ScheduledDay day, Set<String> stillClosed) {
        if (day.stops().stream().noneMatch(stop -> stillClosed.contains(stop.contentId()))) {
            return day;
        }
        List<ScheduledStop> stops = day.stops().stream()
                .map(stop -> {
                    if (!stillClosed.contains(stop.contentId())) {
                        return stop;
                    }
                    List<String> notes = new ArrayList<>(stop.notes());
                    notes.add(CLOSED_STOP_NOTE);
                    return new ScheduledStop(stop.contentId(), stop.title(), stop.order(), stop.reason(),
                            stop.startTime(), stop.endTime(), notes, stop.autoRest(),
                            stop.elevationGainMeters(), stop.inclinePenaltyMinutes());
                })
                .toList();
        return new ScheduledDay(day.dayIndex(), day.date(), stops,
                day.totalTravelMinutes(), day.totalTravelKm(), day.dayNotes());
    }
}
