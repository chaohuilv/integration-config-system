package com.integration.config.config;

import com.integration.config.entity.config.User;
import com.integration.config.repository.config.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 系统初始化器 - 创建默认管理员账号
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemInitializer {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        initAdminUser();
    }

    /**
     * 初始化管理员账号
     */
    private void initAdminUser() {
        // 检查是否已存在admin用户编码
        if (userRepository.existsByUserCode("admin")) {
            log.info("管理员账号已存在，跳过初始化");
            return;
        }

        // 创建默认管理员
        User admin = User.builder()
                .userCode("admin")           // 用户编码，用于登录
                .username("系统管理员")        // 用户名称，用于显示
                .password(passwordEncoder.encode("admin123"))
                .displayName("系统管理员")
                .status("ACTIVE")
                .build();

        userRepository.save(admin);
        log.info("========================================");
        log.info("默认管理员账号已创建");
        log.info("用户编码: admin");
        log.info("密码: admin123");
        log.info("请登录后及时修改密码");
        log.info("========================================");
    }
}
