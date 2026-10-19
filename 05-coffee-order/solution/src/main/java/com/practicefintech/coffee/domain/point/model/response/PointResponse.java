package com.practicefintech.coffee.domain.point.model.response;

import com.practicefintech.coffee.common.entity.UserPoint;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointResponse {

    private Long userId;
    private long balance;

    public static PointResponse from(UserPoint point) {
        return new PointResponse(point.getUserId(), point.getBalance());
    }
}
