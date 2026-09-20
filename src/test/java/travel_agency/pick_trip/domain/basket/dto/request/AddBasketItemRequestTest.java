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

@DisplayName("AddBasketItemRequest")
class AddBasketItemRequestTest {

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

    private Set<ConstraintViolation<AddBasketItemRequest>> validateDesiredStayMinutes(Integer desiredStayMinutes) {
        AddBasketItemRequest request = new AddBasketItemRequest(
                "126508", Priority.PREFERRED, "쌍계사", "https://img/1.jpg", "12", desiredStayMinutes
        );
        return validator.validate(request);
    }

    @Test
    @DisplayName("desiredStayMinutes 가 null 이면 선택값이므로 검증을 통과한다.")
    void acceptNullDesiredStayMinutes() {
        // when
        Set<ConstraintViolation<AddBasketItemRequest>> violations = validateDesiredStayMinutes(null);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("desiredStayMinutes 가 10 미만이면 검증에 실패한다.")
    void rejectTooSmallDesiredStayMinutes() {
        // when
        Set<ConstraintViolation<AddBasketItemRequest>> violations = validateDesiredStayMinutes(9);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("desiredStayMinutes");
    }

    @Test
    @DisplayName("desiredStayMinutes 가 480 초과이면 검증에 실패한다.")
    void rejectTooLargeDesiredStayMinutes() {
        // when
        Set<ConstraintViolation<AddBasketItemRequest>> violations = validateDesiredStayMinutes(481);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("desiredStayMinutes");
    }

    @Test
    @DisplayName("desiredStayMinutes 가 10~480 범위이면 검증을 통과한다.")
    void acceptWithinRangeDesiredStayMinutes() {
        // when
        Set<ConstraintViolation<AddBasketItemRequest>> violations = validateDesiredStayMinutes(90);

        // then
        assertThat(violations).isEmpty();
    }
}
