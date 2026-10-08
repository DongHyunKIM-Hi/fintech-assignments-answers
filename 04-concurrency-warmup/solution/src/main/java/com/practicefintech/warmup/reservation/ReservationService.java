package com.practicefintech.warmup.reservation;

import com.practicefintech.warmup.common.ApiException;
import com.practicefintech.warmup.common.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회의실 예약의 동시성을 책임지는 서비스.
 *
 * 락의 폭: 방 단위. {@link #roomLocks}는 방 ID별로 서로 다른 락 객체를 준다.
 * 그래서 같은 방의 요청은 한 번에 하나씩만 처리되지만, 다른 방끼리는 완전히 병렬로
 * 처리된다 (T4, 시나리오 S10).
 */
@Service
public class ReservationService {

    private final ReservationStore reservationStore;
    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();

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

        Object lock = roomLocks.computeIfAbsent(roomId, k -> new Object());
        synchronized (lock) {
            // T3 대응: "겹침 확인"과 "저장"을 같은 락 안에서 함께 수행해,
            // 그 사이 다른 요청이 같은 시간대를 먼저 차지하지 못하게 한다.
            boolean conflict = reservationStore.findByRoomId(roomId).stream()
                    .anyMatch(r -> r.overlaps(from, to));
            if (conflict) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.TIME_CONFLICT, "이미 겹치는 시간에 예약이 있습니다.");
            }

            Reservation reservation = new Reservation(userId, from, to);
            reservationStore.save(roomId, reservation);
            return reservation;
        }
    }

    public List<RoomReservation> findByUser(String userId) {
        Map<String, List<Reservation>> all = reservationStore.findAll();
        return all.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(r -> r.userId().equals(userId))
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
