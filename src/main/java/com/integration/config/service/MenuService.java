package com.integration.config.service;

import com.integration.config.entity.config.Menu;
import com.integration.config.repository.config.MenuRepository;
import com.integration.config.repository.config.RoleMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * 获取用户的菜单（根据角色ID列表）
     */
    public List<Menu> getUserMenus(List<Long> roleIds) {
        log.info("[MenuService] getUserMenus 调用, roleIds={}", roleIds);
        
        if (roleIds == null || roleIds.isEmpty()) {
            log.warn("[MenuService] 角色ID列表为空，返回空菜单");
            return List.of();
        }
        
        // 通过角色ID查询菜单ID
        List<Long> menuIds = roleMenuRepository.findMenuIdsByRoleIds(roleIds);
        log.info("[MenuService] 查询到的菜单ID列表: {}", menuIds);
        
        if (menuIds.isEmpty()) {
            log.warn("[MenuService] 未找到任何菜单关联");
            return List.of();
        }
        
        // 查询菜单详情，只返回启用的菜单，按排序号排序
        List<Menu> menus = menuRepository.findAllById(menuIds).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .collect(Collectors.toList());
        
        log.info("[MenuService] 返回菜单: {}", menus.stream().map(Menu::getCode).toList());
        return menus;
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

        return menuRepository.save(existing);
    }

    /**
     * 删除菜单
     */
    @Transactional
    public void deleteMenu(Long id) {
        menuRepository.deleteById(id);
    }

    /**
     * 获取所有菜单（用于前端获取完整路由映射）
     */
    public List<Menu> getAllMenusForPageMap() {
        return menuRepository.findAll();
    }

    /**
     * 获取所有列表页菜单（用于角色配置）
     */
    public List<Menu> getListMenus() {
        return menuRepository.findAll().stream()
                .filter(m -> "LIST".equals(m.getPageType()) || m.getPageType() == null)
                .collect(Collectors.toList());
    }
}
