package com.familion.family.application;

import com.familion.common.error.NotFoundException;
import com.familion.common.security.FamilyVisibility;
import com.familion.family.domain.User;
import com.familion.family.infrastructure.persistence.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyMemberService {

    private final UserRepository userRepository;

    public FamilyMemberService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberView> listMembers(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return userRepository.findAllByFamilyIdOrderByCreatedAtAsc(familyId).stream()
                .map(FamilyMemberService::toView)
                .toList();
    }

    @Transactional
    public FamilyMemberView updateVisibility(
            UUID familyId, UUID memberUserId, FamilyVisibility familyVisibility) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(memberUserId, "memberUserId must not be null");
        Objects.requireNonNull(familyVisibility, "familyVisibility must not be null");

        User member = userRepository
                .findByIdAndFamilyId(memberUserId, familyId)
                .orElseThrow(() -> new NotFoundException("Family member not found"));
        member.updateFamilyVisibility(familyVisibility);
        return toView(userRepository.save(member));
    }

    private static FamilyMemberView toView(User user) {
        return new FamilyMemberView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getFamilyVisibility(),
                user.getAccentColor(),
                user.isActive());
    }
}
