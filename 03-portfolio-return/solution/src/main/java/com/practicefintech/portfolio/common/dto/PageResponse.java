package com.practicefintech.portfolio.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 상품·포트폴리오 목록 조회에 공통으로 쓰는 페이지 응답 형식. */
@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
