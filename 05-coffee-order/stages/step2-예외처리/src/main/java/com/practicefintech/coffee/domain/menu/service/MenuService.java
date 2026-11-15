package com.practicefintech.coffee.domain.menu.service;

import com.practicefintech.coffee.domain.menu.model.response.MenuResponse;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    @Transactional(readOnly = true)
    public List<MenuResponse> getMenus() {
        return menuRepository.findAll(Sort.by("id")).stream()
                .map(MenuResponse::from)
                .toList();
    }
}
