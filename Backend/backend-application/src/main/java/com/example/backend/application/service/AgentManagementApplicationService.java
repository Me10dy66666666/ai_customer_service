package com.example.backend.application.service;

import com.example.backend.application.dto.AgentDto;
import com.example.backend.application.dto.CreateAgentCommand;
import com.example.backend.application.dto.UpdateAgentCommand;
import com.example.backend.common.BusinessException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.domain.auth.model.User;
import com.example.backend.domain.auth.repository.RoleRepository;
import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.domain.auth.service.PasswordEncodingService;
import com.example.backend.domain.profile.model.UserProfile;
import com.example.backend.domain.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentManagementApplicationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncodingService passwordEncodingService;
    private final AgentSkillService agentSkillService;
    @Autowired(required = false)
    private UserProfileRepository userProfileRepository;

    private static final Set<String> STAFF_ROLES = Set.of("AGENT", "KB_ADMIN", "ADMIN");

    public Map<String, Object> searchAgents(String keyword, String roleFilter, Integer status,
                                             String startDate, String endDate, int page, int size) {
        List<User> allStaff = userRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream()
                        .anyMatch(r -> STAFF_ROLES.contains(r.getRoleName())))
                .toList();

        List<User> filtered = allStaff.stream()
                .filter(u -> matchKeyword(u, keyword))
                .filter(u -> matchRole(u, roleFilter))
                .filter(u -> matchStatus(u, status))
                .filter(u -> matchDateRange(u, startDate, endDate))
                .toList();

        return buildPageResult(filtered, page, size);
    }

    public Map<String, Object> searchRegularUsers(String keyword, String roleFilter, Integer status,
                                                   String startDate, String endDate, int page, int size) {
        List<User> allRegular = userRepository.findAll().stream()
                .filter(u -> u.getRoles() == null || u.getRoles().stream()
                        .noneMatch(r -> STAFF_ROLES.contains(r.getRoleName())))
                .toList();

        List<User> filtered = allRegular.stream()
                .filter(u -> matchKeyword(u, keyword))
                .filter(u -> matchRole(u, roleFilter))
                .filter(u -> matchStatus(u, status))
                .filter(u -> matchDateRange(u, startDate, endDate))
                .toList();

        return buildPageResult(filtered, page, size);
    }

    public AgentDto getAgent(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        if (!user.isAgent()) {
            throw new BusinessException(400, "User is not an agent");
        }
        return toDto(user);
    }

    @Transactional
    public AgentDto createAgent(CreateAgentCommand command) {
        if (userRepository.existsByUsername(command.getUsername())) {
            throw new BusinessException(400, "Username already exists");
        }
        if (command.getPhone() != null && userRepository.existsByPhone(command.getPhone())) {
            throw new BusinessException(400, "Phone already exists");
        }

        User user = new User();
        user.setUsername(command.getUsername());
        user.setPassword(passwordEncodingService.encode(command.getPassword()));
        user.setNickname(command.getNickname());
        user.setPhone(command.getPhone());
        user.setEmail(command.getEmail());
        user.setStatus(1);

        User saved = userRepository.save(user);

        String roleName = command.getRoleName() != null ? command.getRoleName() : "AGENT";
        if (!"KB_ADMIN".equals(roleName) && !"AGENT".equals(roleName)) {
            roleName = "AGENT";
        }

        Role role = roleRepository.findByRoleName(roleName).orElse(null);
        if (role != null) {
            userRepository.saveUserRole(saved.getId(), role.getId());
        }

        if ("AGENT".equals(roleName) && command.getSkills() != null && !command.getSkills().isEmpty()) {
            agentSkillService.replaceSkills(saved.getId(), command.getSkills());
        }

        return toDto(userRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent created but not found")));
    }

    @Transactional
    public AgentDto updateAgent(Long id, UpdateAgentCommand command) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        if (!user.isAgent()) {
            throw new BusinessException(400, "User is not an agent");
        }

        if (command.getPhone() != null
                && userRepository.existsByPhoneExcludingId(command.getPhone(), id)) {
            throw new BusinessException(400, "Phone already exists");
        }

        if (command.getNickname() != null) {
            user.setNickname(command.getNickname());
        }
        if (command.getPhone() != null) {
            user.setPhone(command.getPhone());
        }
        if (command.getEmail() != null) {
            user.setEmail(command.getEmail());
        }
        if (command.getPassword() != null && !command.getPassword().isBlank()) {
            user.setPassword(passwordEncodingService.encode(command.getPassword()));
        }
        if (command.getStatus() != null) {
            user.setStatus(command.getStatus());
        }

        User saved = userRepository.save(user);

        if (command.getRoleName() != null) {
            String roleName = command.getRoleName();
            if ("KB_ADMIN".equals(roleName) || "AGENT".equals(roleName)) {
                userRepository.clearUserRoles(saved.getId());
                Role role = roleRepository.findByRoleName(roleName).orElse(null);
                if (role != null) {
                    userRepository.saveUserRole(saved.getId(), role.getId());
                }
            }
        }

        if (command.getSkills() != null) {
            agentSkillService.replaceSkills(saved.getId(), command.getSkills());
        }

        return toDto(userRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found after update")));
    }

    @Transactional
    public void deleteAgent(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        if (!user.isAgent()) {
            throw new BusinessException(400, "User is not an agent");
        }

        userRepository.deleteUserRole(id);
        userRepository.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            userRepository.deleteUserRole(id);
            userRepository.deleteById(id);
        }
    }

    @Transactional
    public void batchUpdateStatus(List<Long> ids, Integer status) {
        for (Long id : ids) {
            User user = userRepository.findById(id).orElse(null);
            if (user != null && user.isAgent()) {
                user.setStatus(status);
                userRepository.save(user);
            }
        }
    }

    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(status);
        userRepository.save(user);
    }

    private AgentDto toDto(User user) {
        AgentDto dto = new AgentDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            dto.setRoleName(user.getRoles().iterator().next().getRoleName());
            dto.setRoleNames(user.getRoles().stream()
                    .map(Role::getRoleName)
                    .collect(Collectors.toList()));
        }
        dto.setSkills(agentSkillService.getSkillsByAgentId(user.getId()));
        dto.setTags(fetchUserTags(user.getId()));
        dto.setCreateTime(user.getCreateTime());
        dto.setUpdateTime(user.getUpdateTime());
        return dto;
    }

    private Map<String, Object> buildPageResult(List<User> source, int page, int size) {
        int total = source.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<AgentDto> pageList = (fromIndex < total)
                ? source.subList(fromIndex, toIndex).stream().map(this::toDto).toList()
                : Collections.emptyList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private String fetchUserTags(Long userId) {
        if (userId == null || userProfileRepository == null) return null;
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTags)
                .orElse(null);
    }

    private boolean matchKeyword(User user, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String kw = keyword.toLowerCase();
        return (user.getUsername() != null && user.getUsername().toLowerCase().contains(kw))
                || (user.getNickname() != null && user.getNickname().toLowerCase().contains(kw))
                || (user.getPhone() != null && user.getPhone().contains(kw));
    }

    private boolean matchRole(User user, String roleFilter) {
        if (roleFilter == null || roleFilter.isBlank()) return true;
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> roleFilter.equals(r.getRoleName()));
    }

    private boolean matchStatus(User user, Integer statusFilter) {
        if (statusFilter == null) return true;
        return statusFilter.equals(user.getStatus());
    }

    private boolean matchDateRange(User user, String startDate, String endDate) {
        if (startDate == null && endDate == null) return true;
        LocalDateTime createTime = user.getCreateTime();
        if (createTime == null) return true;
        try {
            if (startDate != null && !startDate.isBlank()) {
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                if (createTime.isBefore(start)) return false;
            }
            if (endDate != null && !endDate.isBlank()) {
                LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
                if (createTime.isAfter(end)) return false;
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }
}
