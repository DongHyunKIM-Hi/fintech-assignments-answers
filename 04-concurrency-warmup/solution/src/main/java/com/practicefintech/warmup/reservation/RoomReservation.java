package com.practicefintech.warmup.reservation;

/** 어느 방의 예약인지를 함께 담은 조합. 사용자별 조회(여러 방을 훑어야 함)에 사용한다. */
public record RoomReservation(String roomId, Reservation reservation) {
}
