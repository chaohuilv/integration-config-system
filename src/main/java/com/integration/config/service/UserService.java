package com.integration.config.service;

import com.integration.config.dto.CreateUserDTO;
import com.integration.config.dto.UserDTO;
import com.integration.config.entity.config.User;
import com.integration.config.enums.AppConstants;
import com.integration.config.repository.config.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "configTransactionManager")
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录验证（使用用户编码）
     */
    public User login(String userCode, String password) {
        Optional<User> userOpt = userRepository.findByUserCode(userCode);
        if (userOpt.isEmpty()) {
            return null;
        }
        User user = userOpt.get();
        if (!AppConstants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return null;
        }
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    /**
     * 更新最后登录信息
     */
    public void updateLoginInfo(Long userId, String clientIp) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginTime(LocalDateTime.now());
            user.setLastLoginIp(clientIp);
            userRepository.save(user);
        });
    }

    /**
     * 根据ID获取用户
     */
    public Optional<User> getById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 根据用户编码获取用户
     */
    public Optional<User> getByUserCode(String userCode) {
        return userRepository.findByUserCode(userCode);
    }

    /**
     * 创建用户
     */
    public User create(CreateUserDTO dto, Long creatorId) {
        // 检查用户编码是否已存在
        if (userRepository.existsByUserCode(dto.getUserCode())) {
            throw new RuntimeException("用户编码已存在");
        }

        User user = User.builder()
                .userCode(dto.getUserCode())
                .username(StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : dto.getUserCode())
                .password(passwordEncoder.encode(dto.getPassword()))
                .displayName(StringUtils.hasText(dto.getDisplayName()) ? dto.getDisplayName() : dto.getUsername())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .status(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : AppConstants.USER_STATUS_ACTIVE)
                .createdBy(creatorId)
                .build();

        return userRepository.save(user);
    }

    /**
     * 更新用户
     */
    public User update(Long id, CreateUserDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (StringUtils.hasText(dto.getUsername())) {
            user.setUsername(dto.getUsername());
        }
        if (StringUtils.hasText(dto.getDisplayName())) {
            user.setDisplayName(dto.getDisplayName());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            user.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        return userRepository.save(user);
    }

    /**
     * 删除用户
     */
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * 分页查询用户
     */
    public Page<UserDTO> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;

        if (StringUtils.hasText(keyword)) {
            // 关键词搜索：用户编码、用户名、显示名称
            userPage = userRepository.findAll((root, query, cb) -> {
                var codeLike = cb.like(root.get("userCode"), "%" + keyword + "%");
                var nameLike = cb.like(root.get("username"), "%" + keyword + "%");
                var displayLike = cb.like(root.get("displayName"), "%" + keyword + "%");
                return cb.or(codeLike, nameLike, displayLike);
            }, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        return userPage.map(this::toDTO);
    }

    /**
     * 获取所有用户（下拉选择用）
     */
    public List<UserDTO> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "displayName")).stream()
                .filter(u -> AppConstants.USER_STATUS_ACTIVE.equals(u.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 转换为DTO
     */
    private UserDTO toDTO(User user) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return UserDTO.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime() != null ? user.getLastLoginTime().format(fmt) : null)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(fmt) : null)
                .build();
    }
}
