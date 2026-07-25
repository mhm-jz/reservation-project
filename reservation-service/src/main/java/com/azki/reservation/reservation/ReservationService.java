package com.azki.reservation.reservation;

import com.azki.reservation.common.exception.IdempotencyKeyReusedException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final AvailableSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationIdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationMapper reservationMapper;

    @Transactional
    public ReservationResponse createReservation(
            Long slotId,
            Long userId
    ) {
        ReservationCreationResult result =
                executeReservationCreation(slotId, userId);

        publishSlotAvailabilityChanged(result.slotDay());
        return result.response();
    }

    @Transactional
    public ReservationResponse createReservation(
            Long slotId,
            Long userId,
            UUID idempotencyKey
    ) {
        String key = idempotencyKey.toString();
        int claimed = idempotencyRepository.claim(
                userId,
                key,
                slotId,
                LocalDateTime.now()
        );

        if (claimed == 0) {
            return replayReservation(userId, key, slotId);
        }

        ReservationCreationResult result =
                executeReservationCreation(slotId, userId);
        ReservationResponse response = result.response();

        int completed = idempotencyRepository.complete(
                userId,
                key,
                response.id(),
                response.startTime(),
                response.endTime(),
                response.createdAt()
        );
        if (completed != 1) {
            throw new IllegalStateException(
                    "Could not store reservation idempotency result"
            );
        }

        publishSlotAvailabilityChanged(result.slotDay());
        return loadIdempotency(userId, key).toResponse();
    }

    private ReservationCreationResult executeReservationCreation(
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

        return new ReservationCreationResult(
                reservationMapper.toResponse(savedReservation),
                slot.getStartTime().toLocalDate()
        );
    }

    private ReservationResponse replayReservation(
            Long userId,
            String idempotencyKey,
            Long requestedSlotId
    ) {
        ReservationIdempotencyEntity idempotency =
                loadIdempotency(userId, idempotencyKey);

        if (!idempotency.hasSlotId(requestedSlotId)) {
            throw new IdempotencyKeyReusedException();
        }

        return idempotency.toResponse();
    }

    private ReservationIdempotencyEntity loadIdempotency(
            Long userId,
            String idempotencyKey
    ) {
        return idempotencyRepository
                .findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation idempotency claim was not found"
                ));
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
        OwnedReservationSlot ownedSlot = loadOwnedReservationSlot(
                reservationId,
                userId
        );
        deleteOwnedReservation(reservationId, userId);
        releaseSlot(ownedSlot.slotId());

        publishSlotAvailabilityChanged(
                ownedSlot.startTime().toLocalDate()
        );
    }

    private OwnedReservationSlot loadOwnedReservationSlot(
            Long reservationId,
            Long userId
    ) {
        return reservationRepository
                .findOwnedSlotByReservationIdAndUserId(
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
        publishSlotAvailabilityChanged(
                slot.getStartTime().toLocalDate()
        );
    }

    private void publishSlotAvailabilityChanged(LocalDate day) {
        eventPublisher.publishEvent(
                new SlotAvailabilityChangedEvent(day)
        );
    }

    private record ReservationCreationResult(
            ReservationResponse response,
            LocalDate slotDay
    ) {
    }
}
