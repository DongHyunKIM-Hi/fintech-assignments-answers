package com.practicefintech.portfolio.domain.portfolio.repository;

import com.practicefintech.portfolio.common.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 1단계는 전체 조회만 한다. 이름 검색({@code findByNameContainingIgnoreCase})은 2단계에서 추가한다.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
