package com.practicefintech.warmup.domain.reservation.controller;

import com.practicefintech.warmup.common.entity.Reservation;
import com.practicefintech.warmup.domain.reservation.model.request.CreateReservationRequest;
import com.practicefintech.warmup.domain.reservation.model.response.ReservationResponse;
import com.practicefintech.warmup.domain.reservation.model.response.RoomReservationView;
import com.practicefintech.warmup.domain.reservation.model.response.UserReservationView;
import com.practicefintech.warmup.domain.reservation.repository.ReservationStore;
import com.practicefintech.warmup.domain.reservation.service.ReservationService;
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
        Reservation saved = reservationService.create(request.getRoomId(), request.getUserId(), request.getFrom(), request.getTo());
        ReservationResponse body = new ReservationResponse(
                request.getRoomId(), saved.getUserId(), saved.getFrom().toString(), saved.getTo().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/rooms/{roomId}")
    public List<RoomReservationView> byRoom(@PathVariable String roomId) {
        return reservationStore.findByRoomId(roomId).stream()
                .map(r -> new RoomReservationView(r.getUserId(), r.getFrom().toString(), r.getTo().toString()))
                .toList();
    }

    @GetMapping("/users/{userId}")
    public List<UserReservationView> byUser(@PathVariable String userId) {
        return reservationService.findByUser(userId).stream()
                .map(rr -> new UserReservationView(
                        rr.getRoomId(), rr.getReservation().getFrom().toString(), rr.getReservation().getTo().toString()))
                .toList();
    }
}
