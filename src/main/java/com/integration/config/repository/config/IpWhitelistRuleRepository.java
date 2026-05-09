package com.integration.config.repository.config;

import com.integration.config.entity.config.IpWhitelistRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IpWhitelistRuleRepository extends JpaRepository<IpWhitelistRule, Long> {

    Optional<IpWhitelistRule> findByRuleCode(String ruleCode);

    List<IpWhitelistRule> findByStatusOrderByPriorityAsc(String status);

    /** 查询所有启用的规则（按优先级排序） */
    @Query("SELECT r FROM IpWhitelistRule r WHERE r.status = 'ACTIVE' ORDER BY r.priority ASC")
    List<IpWhitelistRule> findAllActive();

    /** 查询全局规则 */
    @Query("SELECT r FROM IpWhitelistRule r WHERE r.status = 'ACTIVE' AND r.scope = 'GLOBAL' ORDER BY r.priority ASC")
    List<IpWhitelistRule> findActiveGlobalRules();

    /** 查询指定接口的规则 */
    @Query("SELECT r FROM IpWhitelistRule r WHERE r.status = 'ACTIVE' AND r.scope = 'API' AND r.apiCodes LIKE %:apiCode% ORDER BY r.priority ASC")
    List<IpWhitelistRule> findActiveApiRules(@Param("apiCode") String apiCode);

    /** 检查规则编码是否存在（新建时排除自身） */
    boolean existsByRuleCodeAndIdNot(String ruleCode, Long id);

    /** 检查规则编码是否存在 */
    boolean existsByRuleCode(String ruleCode);

    /** 查询启用的全局规则数量 */
    @Query("SELECT COUNT(r) FROM IpWhitelistRule r WHERE r.status = 'ACTIVE' AND r.scope = 'GLOBAL'")
    long countActiveGlobalRules();
}
