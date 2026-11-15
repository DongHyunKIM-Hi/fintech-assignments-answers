package com.practicefintech.coffee.domain.menu.model.response;

import com.practicefintech.coffee.common.entity.Menu;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MenuResponse {

    private Long menuId;
    private String name;
    private long price;

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice());
    }
}
