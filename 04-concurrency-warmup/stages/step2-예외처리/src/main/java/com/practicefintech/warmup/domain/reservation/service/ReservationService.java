package com.practicefintech.warmup.domain.reservation.service;

import com.practicefintech.warmup.common.entity.Reservation;
import com.practicefintech.warmup.common.enums.ErrorCode;
import com.practicefintech.warmup.common.exception.ApiException;
import com.practicefintech.warmup.domain.reservation.model.dto.RoomReservation;
import com.practicefintech.warmup.domain.reservation.repository.ReservationStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * [2단계: 예외 처리] 시간 검증과 겹침 판정, 에러 응답을 채웁니다. 아직 동시성 제어는 없습니다.
 *
 * 이 단계에서 되는 것 — S6(정상), S7(차례로 겹치면 409), S9(맞닿기만 하면 201), 입력 검증(400)
 * 이 단계에서 여전히 깨지는 것 — S8(동시 5건). 3단계에서 락 범위를 설계해 고칩니다.
 */
@Service
public class ReservationService {

    private final ReservationStore reservationStore;

    public ReservationService(ReservationStore reservationStore) {
        this.reservationStore = reservationStore;
    }

    public Reservation create(String roomId, String userId, String fromText, String toText) {
        if (roomId == null || roomId.isBlank() || userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "roomId, userId는 필수입니다.");
        }
        LocalTime from = parseTime(fromText);
        LocalTime to = parseTime(toText);
        if (!from.isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "from은 to보다 빨라야 합니다.");
        }

        boolean conflict = reservationStore.findByRoomId(roomId).stream()
                .anyMatch(r -> r.overlaps(from, to));
        if (conflict) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.TIME_CONFLICT, "이미 겹치는 시간에 예약이 있습니다.");
        }

        // 여기서 "겹침 확인"과 "저장" 사이에 다른 요청이 끼어들 수 있다 -> 3단계에서 해결한다.
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

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "시간 형식은 HH:mm이어야 합니다: " + text);
        }
    }
}
