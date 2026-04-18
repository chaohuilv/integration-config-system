package com.integration.config.repository.config;

import com.integration.config.entity.config.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 菜单仓库
 */
@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    Optional<Menu> findByCode(String code);

    boolean existsByCode(String code);

    List<Menu> findByStatus(String status);

    List<Menu> findByParentIdOrderBySortOrderAsc(Long parentId);

    @Query("SELECT m FROM Menu m WHERE m.status = 'ACTIVE' ORDER BY m.section, m.sortOrder")
    List<Menu> findAllActiveOrderBySortOrder();

    @Query("SELECT m FROM Menu m ORDER BY m.section, m.sortOrder")
    List<Menu> findAllOrderBySortOrder();

    @Query("SELECT m FROM Menu m WHERE m.parentId IS NULL AND m.status = 'ACTIVE' ORDER BY m.sortOrder")
    List<Menu> findTopLevelActive();

    @Query("SELECT DISTINCT m.section FROM Menu m WHERE m.section IS NOT NULL ORDER BY m.section")
    List<String> findAllSections();
}
