package com.nexacart.user.service;

import com.nexacart.common.exception.ApiException;
import com.nexacart.common.response.PageResponse;
import com.nexacart.user.dto.AdminUpdateUserRequest;
import com.nexacart.user.dto.UpdateProfileRequest;
import com.nexacart.user.dto.UserResponse;
import com.nexacart.user.entity.Role;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(getRequired(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getRequired(userId);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users;
        if (query == null || query.isBlank()) {
            users = userRepository.findAll(pageable);
        } else {
            String term = query.trim();
            users = userRepository
                    .findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                            term, term, term, pageable);
        }
        return PageResponse.from(users.map(UserResponse::from));
    }

    @Transactional
    public UserResponse adminUpdateUser(UUID actorId, UUID userId, AdminUpdateUserRequest request) {
        User user = getRequired(userId);
        if (user.getId().equals(actorId) && (request.role() != Role.ADMIN || !request.enabled())) {
            throw ApiException.badRequest(
                    "SELF_ADMIN_CHANGE_NOT_ALLOWED",
                    "Administrators cannot demote or disable their own account"
            );
        }
        boolean wasActiveAdmin = user.getRole() == Role.ADMIN && user.isEnabled();
        boolean willBeActiveAdmin = request.role() == Role.ADMIN && request.enabled();
        if (wasActiveAdmin && !willBeActiveAdmin && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "At least one enabled administrator must remain");
        }
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User getRequired(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }
}
