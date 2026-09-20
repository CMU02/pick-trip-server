package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.basket.entity.Priority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * priority·desiredStayMinutes 모두 부분 갱신용 선택값이므로 {@code null} 은 검증을 통과해야 한다.
 */
@DisplayName("UpdateBasketItemRequest")
class UpdateBasketItemRequestTest {

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

    @Test
    @DisplayName("두 필드 모두 null 이면 검증에 실패한다 (변경할 필드가 없는 빈 요청은 거부한다).")
    void rejectBothNull() {
        // given
        UpdateBasketItemRequest request = new UpdateBasketItemRequest(null, null);

        // when
        Set<ConstraintViolation<UpdateBasketItemRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("desiredStayMinutes 가 10 미만이면 검증에 실패한다.")
    void rejectTooSmallDesiredStayMinutes() {
        // given
        UpdateBasketItemRequest request = new UpdateBasketItemRequest(null, 9);

        // when
        Set<ConstraintViolation<UpdateBasketItemRequest>> violations = validator.validate(request);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("desiredStayMinutes");
    }

    @Test
    @DisplayName("desiredStayMinutes 가 480 초과이면 검증에 실패한다.")
    void rejectTooLargeDesiredStayMinutes() {
        // given
        UpdateBasketItemRequest request = new UpdateBasketItemRequest(null, 481);

        // when
        Set<ConstraintViolation<UpdateBasketItemRequest>> violations = validator.validate(request);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("desiredStayMinutes");
    }

    @Test
    @DisplayName("priority 와 desiredStayMinutes 가 유효 범위이면 검증을 통과한다.")
    void acceptValidValues() {
        // given
        UpdateBasketItemRequest request = new UpdateBasketItemRequest(Priority.OPTIONAL, 90);

        // when
        Set<ConstraintViolation<UpdateBasketItemRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }
}
