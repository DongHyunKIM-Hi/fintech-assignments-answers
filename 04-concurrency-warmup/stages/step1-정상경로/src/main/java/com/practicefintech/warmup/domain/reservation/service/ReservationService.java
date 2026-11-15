package com.practicefintech.warmup.domain.reservation.service;

import com.practicefintech.warmup.common.entity.Reservation;
import com.practicefintech.warmup.domain.reservation.model.dto.RoomReservation;
import com.practicefintech.warmup.domain.reservation.repository.ReservationStore;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * [1단계: 정상 경로] 겹치지 않는 예약이 정상으로 저장되는지만 확인합니다.
 *
 * 이 단계에서 일부러 하지 않는 것
 * - 시간 형식 검증과 from &gt;= to 검사, 겹침 시 409 (2단계)
 * - 동시성 제어 (3단계)
 */
@Service
public class ReservationService {

    private final ReservationStore reservationStore;

    public ReservationService(ReservationStore reservationStore) {
        this.reservationStore = reservationStore;
    }

    public Reservation create(String roomId, String userId, String fromText, String toText) {
        LocalTime from = LocalTime.parse(fromText);
        LocalTime to = LocalTime.parse(toText);

        Reservation reservation = new Reservation(userId, from, to);
        reservationStore.save(roomId, reservation);
        return reservation;
    }

    public List<RoomReservation> findByUser(String userId) {
        Map<String, List<Reservation>> all = reservationStore.findAll();
        return all.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(r -> r.getUserId().equals(userId))
                        .map(r -> new RoomReservation(entry.getKey(), r)))
                .toList();
    }
}
