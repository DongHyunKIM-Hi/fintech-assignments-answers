package com.practicefintech.portfolio.domain.portfolio.repository;

import com.practicefintech.portfolio.common.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByNameContainingIgnoreCase(String name);
}
