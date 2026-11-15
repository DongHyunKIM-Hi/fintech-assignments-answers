package com.practicefintech.coffee.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 커피 메뉴. data.sql로 미리 넣어 두고 조회만 한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    private Long id;

    private String name;

    private long price;
}
