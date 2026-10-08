package com.practicefintech.warmup.reservation;

import com.practicefintech.warmup.reservation.dto.CreateReservationRequest;
import com.practicefintech.warmup.reservation.dto.ReservationResponse;
import com.practicefintech.warmup.reservation.dto.RoomReservationView;
import com.practicefintech.warmup.reservation.dto.UserReservationView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationStore reservationStore;
    private final ReservationService reservationService;

    public ReservationController(ReservationStore reservationStore, ReservationService reservationService) {
        this.reservationStore = reservationStore;
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@RequestBody CreateReservationRequest request) {
        Reservation saved = reservationService.create(request.roomId(), request.userId(), request.from(), request.to());
        ReservationResponse body = new ReservationResponse(
                request.roomId(), saved.userId(), saved.from().toString(), saved.to().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/rooms/{roomId}")
    public List<RoomReservationView> byRoom(@PathVariable String roomId) {
        return reservationStore.findByRoomId(roomId).stream()
                .map(r -> new RoomReservationView(r.userId(), r.from().toString(), r.to().toString()))
                .toList();
    }

    @GetMapping("/users/{userId}")
    public List<UserReservationView> byUser(@PathVariable String userId) {
        return reservationService.findByUser(userId).stream()
                .map(rr -> new UserReservationView(
                        rr.roomId(), rr.reservation().from().toString(), rr.reservation().to().toString()))
                .toList();
    }
}
