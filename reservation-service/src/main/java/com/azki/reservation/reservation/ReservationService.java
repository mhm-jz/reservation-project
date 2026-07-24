package com.azki.reservation.reservation;

import com.azki.reservation.common.exception.ReservationNotFoundException;
import com.azki.reservation.common.exception.ReservationStateException;
import com.azki.reservation.common.exception.SlotAlreadyReservedException;
import com.azki.reservation.common.exception.SlotNotFoundException;
import com.azki.reservation.common.exception.SlotUnavailableException;
import com.azki.reservation.reservation.mapper.ReservationMapper;
import com.azki.reservation.reservation.dto.ReservationResponse;
import com.azki.reservation.slot.AvailableSlotEntity;
import com.azki.reservation.slot.AvailableSlotRepository;
import com.azki.reservation.slot.SlotAvailabilityChangedEvent;
import com.azki.reservation.user.UserEntity;
import com.azki.reservation.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final AvailableSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationMapper reservationMapper;

    @Transactional
    public ReservationResponse createReservation(
            Long slotId,
            Long userId
    ) {
        LocalDateTime now = LocalDateTime.now();
        reserveSlotOrThrow(slotId, now);

        AvailableSlotEntity slot = loadSlot(slotId);
        ReservationEntity reservation =
                buildReservation(userId, slot);
        ReservationEntity savedReservation =
                reservationRepository.save(reservation);

        publishSlotAvailabilityChanged(slot);
        return reservationMapper.toResponse(savedReservation);
    }

    private void reserveSlotOrThrow(
            Long slotId,
            LocalDateTime now
    ) {
        int updatedRows = slotRepository.reserveIfAvailable(slotId, now);
        if (updatedRows == 0) {
            throwSlotReservationError(slotId);
        }
    }

    private void throwSlotReservationError(Long slotId) {
        AvailableSlotEntity slot = loadSlot(slotId);
        if (slot.isReserved()) {
            throw new SlotAlreadyReservedException(slotId);
        }
        throw new SlotUnavailableException(slotId);
    }

    @Transactional
    public void cancelReservation(
            Long reservationId,
            Long userId
    ) {
        Long slotId = loadOwnedReservationSlotId(
                reservationId,
                userId
        );
        deleteOwnedReservation(reservationId, userId);
        releaseSlot(slotId);

        AvailableSlotEntity slot = loadSlot(slotId);
        publishSlotAvailabilityChanged(slot);
    }

    private Long loadOwnedReservationSlotId(
            Long reservationId,
            Long userId
    ) {
        return reservationRepository
                .findSlotIdByReservationIdAndUserId(
                        reservationId,
                        userId
                )
                .orElseThrow(() ->
                        new ReservationNotFoundException(reservationId)
                );
    }

    private void deleteOwnedReservation(
            Long reservationId,
            Long userId
    ) {
        int deletedRows = reservationRepository
                .deleteByReservationIdAndUserId(reservationId, userId);
        if (deletedRows == 0) {
            throw new ReservationNotFoundException(reservationId);
        }
    }

    private void releaseSlot(Long slotId) {
        int releasedRows = slotRepository
                .releaseReservedSlot(slotId);
        if (releasedRows == 0) {
            throw new ReservationStateException();
        }
    }

    private AvailableSlotEntity loadSlot(Long slotId) {
        return slotRepository
                .findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));
    }

    private ReservationEntity buildReservation(
            Long userId,
            AvailableSlotEntity slot
    ) {
        UserEntity user = userRepository.getReferenceById(userId);
        return new ReservationEntity(user, slot);
    }

    private void publishSlotAvailabilityChanged(
            AvailableSlotEntity slot
    ) {
        eventPublisher.publishEvent(
                new SlotAvailabilityChangedEvent(
                        slot.getStartTime().toLocalDate()
                )
        );
    }
}
