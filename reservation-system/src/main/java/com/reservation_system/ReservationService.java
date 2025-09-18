package com.reservation_system;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReservationService {

    private final Map<Long, Reservation> reservationMap;
    private final AtomicLong idCounter;

    private final ReservationRepository repository;

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
        reservationMap = new HashMap<>();
        idCounter = new AtomicLong();
    }

    public Reservation getReservationById(Long id) {
        ReservationEntity reservationEntity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Not found reservation by id: " + id));

        return toDomainReservation(reservationEntity);
    }

    public List<Reservation> findAllReservations() {

        List<ReservationEntity> allEntities = repository.findAll();

        return allEntities.stream()
                .map(this::toDomainReservation).toList();
    }

    public Reservation createReservation(Reservation reservationToCreate) {
        if(reservationToCreate.id() != null) {
            throw new IllegalArgumentException("Id should be empty");
        }

        if(reservationToCreate.status() != null) {
            throw new IllegalArgumentException("Status should be empty");
        }

        var entityToSave = new ReservationEntity(
                null,
                reservationToCreate.userId(),
                reservationToCreate.roomId(),
                reservationToCreate.startDate(),
                reservationToCreate.endDate(),
                ReservationStatus.PENDING
        );

        var savedEntity = repository.save(entityToSave);
        return toDomainReservation(savedEntity);
    }

    public Reservation updateReservation(Long id, Reservation reservationToUpdate) {

        var reservationEntity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Not found reservation by id: " + id));

        var reservation = reservationMap.get(id);
        if(reservationEntity.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Cannot modify reservation: status=" + reservationEntity.getStatus());
        }

        var updatedReservation = new ReservationEntity(
                reservationEntity.getId(),
                reservationEntity.getUserId(),
                reservationEntity.getRoomId(),
                reservationEntity.getStartDate(),
                reservationEntity.getEndDate(),
                ReservationStatus.PENDING
        );

        var reservationToSave = repository.save(updatedReservation);

        return toDomainReservation(reservationToSave);
    }

    public void deleteReservation(Long id) {
        if(!reservationMap.containsKey(id)) {
            throw new NoSuchElementException("Not found reservation by id: " + id);
        }
        reservationMap.remove(id);
    }

    public Reservation approveReservation(Long id) {
        if(!reservationMap.containsKey(id)) {
            throw new NoSuchElementException("Not found reservation by id: " + id);
        }
        var reservation = reservationMap.get(id);
        if(reservation.status() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Cannot approve reservation: status=" + reservation.status());
        }

        var isConflict = isReservationConflict(reservation);
        if(isConflict) {
            throw new IllegalStateException("Cannot approve reservation because of conflict");
        }

        var approvedReservation = new Reservation(
                reservation.id(),
                reservation.userId(),
                reservation.roomId(),
                reservation.startDate(),
                reservation.endDate(),
                ReservationStatus.APPROVED
        );

        reservationMap.put(reservation.id(), approvedReservation);
        return approvedReservation;
    }

    private boolean isReservationConflict(Reservation reservation) {
        for(Reservation existingReservation : reservationMap.values()) {
            if(reservation.id().equals(existingReservation.id())) {
                continue;
            }
            if(!reservation.roomId().equals(existingReservation.roomId())) {
                continue;
            }
            if(!existingReservation.status().equals(ReservationStatus.APPROVED)) {
                continue;
            }
            if(reservation.startDate().isBefore(existingReservation.endDate()) && existingReservation.startDate().isBefore(reservation.endDate())) {
                return true;
            }
        }
        return false;
    }

    private Reservation toDomainReservation(ReservationEntity reservationEntity) {
        return new Reservation(
                reservationEntity.getId(),
                reservationEntity.getUserId(),
                reservationEntity.getRoomId(),
                reservationEntity.getStartDate(),
                reservationEntity.getEndDate(),
                reservationEntity.getStatus()
        );
    }
}
