package travel_agency.pick_trip.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.user.dto.response.UserMeResponse;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.UserException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID uid) {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return new UserMeResponse(
                user.getUid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                user.getCreatedAt()
        );
    }
}
