package com.practicefintech.warmup.domain.reservation.model.dto;

import com.practicefintech.warmup.common.entity.Reservation;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 어느 방의 예약인지를 함께 담은 조합. 사용자별 조회(여러 방을 훑어야 함)에 사용한다. */
@Getter
@AllArgsConstructor
public class RoomReservation {
    private String roomId;
    private Reservation reservation;
}
