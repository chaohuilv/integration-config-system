package com.integration.config.repository.config;

import com.integration.config.entity.config.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * 根据用户编码查找用户（用于登录）
     */
    Optional<User> findByUserCode(String userCode);

    /**
     * 检查用户编码是否存在
     */
    boolean existsByUserCode(String userCode);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);
}
