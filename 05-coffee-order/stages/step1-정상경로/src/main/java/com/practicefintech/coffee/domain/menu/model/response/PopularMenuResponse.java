package com.practicefintech.coffee.domain.menu.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PopularMenuResponse {

    private int rank;
    private Long menuId;
    private String name;
    private long price;
    private long orderCount;
}
