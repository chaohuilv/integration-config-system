package com.integration.config.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.integration.config.entity.config.Menu;
import com.integration.config.repository.config.MenuRepository;
import com.integration.config.repository.config.RoleMenuRepository;
import com.integration.config.enums.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final MenuRepository menuRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 用户菜单缓存 key 前缀 */
    private static final String CACHE_USER_MENUS_PREFIX = AppConstants.REDIS_USER_MENUS_PREFIX;
    /** 全局 pageMap 缓存 key */
    private static final String CACHE_PAGE_MAP = AppConstants.REDIS_GLOBAL_PAGE_MAP;
    /** 缓存过期时间（秒） */
    private static final int CACHE_TTL = AppConstants.CACHE_TTL_SECONDS;

    /**
     * 获取所有菜单
     */
    public List<Menu> getAllMenus() {
        return menuRepository.findAllOrderBySortOrder();
    }

    /**
     * 获取所有启用的菜单
     */
    public List<Menu> getActiveMenus() {
        return menuRepository.findAllActiveOrderBySortOrder();
    }

    /**
     * 按分组获取菜单
     */
    public Map<String, List<Menu>> getMenusGroupBySection() {
        List<Menu> menus = getActiveMenus();
        return menus.stream()
                .filter(m -> m.getSection() != null)
                .collect(Collectors.groupingBy(Menu::getSection));
    }

    /**
     * 获取用户的菜单ID集合（通过角色ID列表）
     * 使用 Redis 缓存，避免每次查 DB
     */
    public Set<Long> getUserMenuIds(Long userId, List<Long> roleIds) {
        // 1. 先查 Redis 缓存
        try {
            String key = CACHE_USER_MENUS_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
                log.debug("[MenuService] 用户菜单缓存命中: userId={}, size={}", userId, ids.size());
                return new HashSet<>(ids);
            }
        } catch (Exception e) {
            log.warn("[MenuService] 读取用户菜单缓存失败: userId={}, error={}", userId, e.getMessage());
        }

        // 2. 缓存未命中，查 DB
        Set<Long> menuIds;
        if (roleIds == null || roleIds.isEmpty()) {
            menuIds = Set.of();
        } else {
            menuIds = new HashSet<>(roleMenuRepository.findMenuIdsByRoleIds(roleIds));
        }

        // 3. 回写缓存
        try {
            String key = CACHE_USER_MENUS_PREFIX + userId;
            String json = objectMapper.writeValueAsString(new ArrayList<>(menuIds));
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(CACHE_TTL));
            log.debug("[MenuService] 写入用户菜单缓存: userId={}, size={}", userId, menuIds.size());
        } catch (Exception e) {
            log.warn("[MenuService] 写入用户菜单缓存失败: userId={}, error={}", userId, e.getMessage());
        }
        return menuIds;
    }

    /**
     * 获取用户的菜单列表（根据角色ID列表）
     * 委托给 getUserMenuIds 后查菜单详情
     */
    public List<Menu> getUserMenus(List<Long> roleIds) {
        return getUserMenus(null, roleIds);
    }

    /**
     * 获取用户的菜单列表（根据用户ID）
     */
    public List<Menu> getUserMenus(Long userId, List<Long> roleIds) {
        log.info("[MenuService] getUserMenus 调用, userId={}, roleIds={}", userId, roleIds);

        Set<Long> menuIds = getUserMenuIds(userId, roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }

        // 查询菜单详情，只返回启用的菜单，按排序号排序
        return menuRepository.findAllById(menuIds).stream()
                .filter(m -> AppConstants.USER_STATUS_ACTIVE.equals(m.getStatus()))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .collect(Collectors.toList());
    }

    /**
     * 创建菜单
     */
    @Transactional
    public Menu createMenu(Menu menu) {
        if (menuRepository.existsByCode(menu.getCode())) {
            throw new RuntimeException("菜单编码已存在: " + menu.getCode());
        }
        return menuRepository.save(menu);
    }

    /**
     * 更新菜单
     * 菜单变更时清除 pageMap 全局缓存
     */
    @Transactional
    public Menu updateMenu(Long id, Menu menu) {
        Menu existing = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在: " + id));

        if (menuRepository.existsByCode(menu.getCode()) && !existing.getCode().equals(menu.getCode())) {
            throw new RuntimeException("菜单编码已存在: " + menu.getCode());
        }

        existing.setCode(menu.getCode());
        existing.setName(menu.getName());
        existing.setIcon(menu.getIcon());
        existing.setPath(menu.getPath());
        existing.setParentId(menu.getParentId());
        existing.setSection(menu.getSection());
        existing.setSortOrder(menu.getSortOrder());
        existing.setStatus(menu.getStatus());

        clearPageMapCache();
        return menuRepository.save(existing);
    }

    /**
     * 删除菜单
     * 菜单删除时清除 pageMap 全局缓存
     */
    @Transactional
    public void deleteMenu(Long id) {
        menuRepository.deleteById(id);
        clearPageMapCache();
    }

    /**
     * 获取所有菜单（用于前端获取完整路由映射）
     * pageMap 全局缓存，所有用户共享
     */
    @SuppressWarnings("unchecked")
    public List<Menu> getAllMenusForPageMap() {
        // pageMap 缓存在 AuthController 层使用，这里仍返回完整列表
        // 缓存的是最终的 Map<String, String>，由 getPageMap() 提供
        return menuRepository.findAll();
    }

    /**
     * 获取 pageMap（path → pageFile 映射）
     * 使用 Redis 全局缓存，菜单变更时清除
     */
    public Map<String, String> getPageMap() {
        // 1. 先查 Redis
        try {
            String json = redisTemplate.opsForValue().get(CACHE_PAGE_MAP);
            if (json != null) {
                log.debug("[MenuService] pageMap 缓存命中");
                return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            }
        } catch (Exception e) {
            log.warn("[MenuService] 读取 pageMap 缓存失败: error={}", e.getMessage());
        }

        // 2. 查 DB 构建
        Map<String, String> pageMap = new HashMap<>();
        for (Menu menu : menuRepository.findAll()) {
            if (menu.getPath() != null && menu.getPageFile() != null) {
                pageMap.put(menu.getPath(), menu.getPageFile());
            }
        }

        // 3. 回写缓存
        try {
            String json = objectMapper.writeValueAsString(pageMap);
            redisTemplate.opsForValue().set(CACHE_PAGE_MAP, json, Duration.ofSeconds(CACHE_TTL));
            log.debug("[MenuService] 写入 pageMap 缓存, size={}", pageMap.size());
        } catch (Exception e) {
            log.warn("[MenuService] 写入 pageMap 缓存失败: error={}", e.getMessage());
        }
        return pageMap;
    }

    /**
     * 获取所有列表页菜单（用于角色配置）
     */
    public List<Menu> getListMenus() {
        return menuRepository.findAll().stream()
                .filter(m -> "LIST".equals(m.getPageType()) || m.getPageType() == null)
                .collect(Collectors.toList());
    }

    // ==================== Redis 缓存方法 ====================

    /**
     * 清除指定用户的菜单缓存
     */
    public void clearUserMenuCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_USER_MENUS_PREFIX + userId);
            log.debug("[MenuService] 已清除用户菜单缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("[MenuService] 清除用户菜单缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 清除 pageMap 全局缓存
     */
    private void clearPageMapCache() {
        try {
            redisTemplate.delete(CACHE_PAGE_MAP);
            log.debug("[MenuService] 已清除 pageMap 全局缓存");
        } catch (Exception e) {
            log.warn("[MenuService] 清除 pageMap 缓存失败: error={}", e.getMessage());
        }
    }
}
