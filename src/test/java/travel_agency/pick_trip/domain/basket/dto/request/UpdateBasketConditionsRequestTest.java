package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * duration 누락이 바구니에 저장된 값을 조용히 지우고, 그 실패가 한참 뒤 일정 생성에서야
 * 드러나던 문제(#43)의 재발 방지 테스트.
 */
@DisplayName("UpdateBasketConditionsRequest")
class UpdateBasketConditionsRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<UpdateBasketConditionsRequest>> validateDuration(Integer duration) {
        UpdateBasketConditionsRequest request = new UpdateBasketConditionsRequest(
                Region.YEONGJU,
                LocalDate.of(2026, 8, 20),
                duration,
                Set.of(TravelCondition.WITH_PARENTS)
        );
        return validator.validate(request);
    }

    @Test
    @DisplayName("duration 이 null 이면 검증에 실패한다.")
    void rejectNullDuration() {
        // given
        Integer duration = null;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("duration");
    }

    @Test
    @DisplayName("duration 이 0 이면 검증에 실패한다.")
    void rejectZeroDuration() {
        // given
        Integer duration = 0;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("duration");
    }

    @Test
    @DisplayName("duration 이 음수이면 검증에 실패한다.")
    void rejectNegativeDuration() {
        // given
        Integer duration = -1;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("duration");
    }

    @Test
    @DisplayName("duration 이 양수이면 검증을 통과한다.")
    void acceptPositiveDuration() {
        // given
        Integer duration = 3;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations).isEmpty();
    }
}
