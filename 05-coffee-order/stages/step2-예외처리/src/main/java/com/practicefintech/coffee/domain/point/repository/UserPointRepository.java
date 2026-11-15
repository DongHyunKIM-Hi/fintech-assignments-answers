package com.practicefintech.coffee.domain.point.repository;

import com.practicefintech.coffee.common.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointRepository extends JpaRepository<UserPoint, Long> {
}
