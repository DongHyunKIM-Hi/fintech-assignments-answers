package com.practicefintech.coffee.domain.menu.controller;

import com.practicefintech.coffee.domain.menu.model.response.MenuResponse;
import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.service.MenuService;
import com.practicefintech.coffee.domain.menu.service.PopularMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final PopularMenuService popularMenuService;

    @GetMapping
    public List<MenuResponse> getMenus() {
        return menuService.getMenus();
    }

    @GetMapping("/popular")
    public List<PopularMenuResponse> getPopularMenus() {
        return popularMenuService.getPopularMenus();
    }
}
