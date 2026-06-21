package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiCodeResponse.Item;
import travel_agency.pick_trip.domain.content.entity.CategoryCode;
import travel_agency.pick_trip.domain.content.entity.LclsSystmCode;
import travel_agency.pick_trip.domain.content.repository.CategoryCodeRepository;
import travel_agency.pick_trip.domain.content.repository.LclsSystmCodeRepository;
import travel_agency.pick_trip.domain.region.entity.AreaCode;
import travel_agency.pick_trip.domain.region.entity.LdongCode;
import travel_agency.pick_trip.domain.region.repository.AreaCodeRepository;
import travel_agency.pick_trip.domain.region.repository.LdongCodeRepository;

/**
 * TourAPI 코드 seed (수집 2·3단계). {@code /areaCode2}, {@code /ldongCode2}, {@code /categoryCode2},
 * {@code /lclsSystmCode2}를 호출해 코드 테이블에 upsert한다.
 *
 * <p>각 코드군 호출은 {@link FeignException}을 잡아 한 코드군의 실패가 다른 코드군 seed를 막지 않게 하며,
 * 실패 시 기존 데이터를 유지하고 원인을 로그로 남긴다(예외 없는 외부 API 호출 금지).
 *
 * <p>운영용 자동 동기화(@Scheduled)는 이슈 C에서 다룬다. 본 서비스는 수동 트리거를 전제로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSeedService {

    private static final int PAGE_SIZE = 100;
    private static final String ROOT = "";

    private final TourApiClient tourApiClient;
    private final AreaCodeRepository areaCodeRepository;
    private final LdongCodeRepository ldongCodeRepository;
    private final CategoryCodeRepository categoryCodeRepository;
    private final LclsSystmCodeRepository lclsSystmCodeRepository;

    /** 전체 코드 seed. */
    @Transactional
    public void seedAll() {
        doAreaCodes();
        doLdongCodes();
        doCategoryCodes();
        doLclsSystmCodes();
    }

    @Transactional
    public void seedAreaCodes() {
        doAreaCodes();
    }

    @Transactional
    public void seedLdongCodes() {
        doLdongCodes();
    }

    @Transactional
    public void seedCategoryCodes() {
        doCategoryCodes();
    }

    @Transactional
    public void seedLclsSystmCodes() {
        doLclsSystmCodes();
    }

    // --- 지역 코드 (/areaCode2): 시도 → 시군구 ---

    private void doAreaCodes() {
        try {
            for (Item sido : tourApiClient.getAreaCode(null, 1, PAGE_SIZE).codeItems()) {
                upsertAreaCode(sido.code(), sido.name(), ROOT);
                for (Item sigungu : tourApiClient.getAreaCode(sido.code(), 1, PAGE_SIZE).codeItems()) {
                    upsertAreaCode(sigungu.code(), sigungu.name(), sido.code());
                }
            }
        } catch (FeignException e) {
            log.warn("지역 코드(/areaCode2) seed 실패 - 기존 데이터 유지: {}", e.getMessage());
        }
    }

    private void upsertAreaCode(String code, String name, String parentCode) {
        areaCodeRepository.findByCodeAndParentCode(code, parentCode)
                .ifPresentOrElse(
                        existing -> existing.update(name),
                        () -> areaCodeRepository.save(
                                AreaCode.builder().code(code).name(name).parentCode(parentCode).build())
                );
    }

    // --- 법정동 코드 (/ldongCode2): 시도 → 시군구 ---

    private void doLdongCodes() {
        try {
            for (Item sido : tourApiClient.getLdongCode(null, 1, PAGE_SIZE).codeItems()) {
                upsertLdongCode(sido.code(), ROOT, sido.name());
                for (Item sigungu : tourApiClient.getLdongCode(sido.code(), 1, PAGE_SIZE).codeItems()) {
                    upsertLdongCode(sido.code(), sigungu.code(), sigungu.name());
                }
            }
        } catch (FeignException e) {
            log.warn("법정동 코드(/ldongCode2) seed 실패 - 기존 데이터 유지: {}", e.getMessage());
        }
    }

    private void upsertLdongCode(String regnCd, String signguCd, String name) {
        ldongCodeRepository.findByRegnCdAndSignguCd(regnCd, signguCd)
                .ifPresentOrElse(
                        existing -> existing.update(name),
                        () -> ldongCodeRepository.save(
                                LdongCode.builder().regnCd(regnCd).signguCd(signguCd).name(name).build())
                );
    }

    // --- 분류 코드 (/categoryCode2): 대분류 → 중분류 → 소분류 ---

    private void doCategoryCodes() {
        try {
            for (Item c1 : tourApiClient.getCategoryCode(null, null, 1, PAGE_SIZE).codeItems()) {
                upsertCategoryCode(c1.code(), c1.name(), ROOT, 1);
                for (Item c2 : tourApiClient.getCategoryCode(c1.code(), null, 1, PAGE_SIZE).codeItems()) {
                    upsertCategoryCode(c2.code(), c2.name(), c1.code(), 2);
                    for (Item c3 : tourApiClient.getCategoryCode(c1.code(), c2.code(), 1, PAGE_SIZE).codeItems()) {
                        upsertCategoryCode(c3.code(), c3.name(), c2.code(), 3);
                    }
                }
            }
        } catch (FeignException e) {
            log.warn("분류 코드(/categoryCode2) seed 실패 - 기존 데이터 유지: {}", e.getMessage());
        }
    }

    private void upsertCategoryCode(String code, String name, String parentCode, int depth) {
        categoryCodeRepository.findByCodeAndParentCode(code, parentCode)
                .ifPresentOrElse(
                        existing -> existing.update(name),
                        () -> categoryCodeRepository.save(
                                CategoryCode.builder().code(code).name(name).parentCode(parentCode).depth(depth).build())
                );
    }

    // --- 신분류체계 코드 (/lclsSystmCode2): 1 → 2 → 3 Depth ---

    private void doLclsSystmCodes() {
        try {
            for (Item d1 : tourApiClient.getLclsSystmCode(null, null, 1, PAGE_SIZE).codeItems()) {
                upsertLclsSystmCode(d1.code(), d1.name(), ROOT, 1);
                for (Item d2 : tourApiClient.getLclsSystmCode(d1.code(), null, 1, PAGE_SIZE).codeItems()) {
                    upsertLclsSystmCode(d2.code(), d2.name(), d1.code(), 2);
                    for (Item d3 : tourApiClient.getLclsSystmCode(d1.code(), d2.code(), 1, PAGE_SIZE).codeItems()) {
                        upsertLclsSystmCode(d3.code(), d3.name(), d2.code(), 3);
                    }
                }
            }
        } catch (FeignException e) {
            log.warn("신분류체계 코드(/lclsSystmCode2) seed 실패 - 기존 데이터 유지: {}", e.getMessage());
        }
    }

    private void upsertLclsSystmCode(String code, String name, String parentCode, int depth) {
        lclsSystmCodeRepository.findByCodeAndParentCode(code, parentCode)
                .ifPresentOrElse(
                        existing -> existing.update(name),
                        () -> lclsSystmCodeRepository.save(
                                LclsSystmCode.builder().code(code).name(name).parentCode(parentCode).depth(depth).build())
                );
    }
}
