package com.practicefintech.portfolio.common;

import java.util.List;

/** 상품·포트폴리오 목록 조회에 공통으로 쓰는 페이지 응답 형식. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
}
