package com.practicefintech.portfolio.domain.product.repository;

import com.practicefintech.portfolio.common.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 1단계는 전체 조회만 한다. 검색, 필터, 정렬, 페이지가 없어서
 * {@code JpaSpecificationExecutor}도 아직 필요 없다. (2단계에서 추가)
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
