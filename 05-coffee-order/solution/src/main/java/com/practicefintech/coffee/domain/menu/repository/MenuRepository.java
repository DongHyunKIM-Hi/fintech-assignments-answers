package com.practicefintech.coffee.domain.menu.repository;

import com.practicefintech.coffee.common.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {
}
