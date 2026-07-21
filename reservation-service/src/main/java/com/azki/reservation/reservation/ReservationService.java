package com.azki.reservation.reservation;

import com.azki.reservation.exception.*;
import com.azki.reservation.reservation.dto.ReservationResponse;
import com.azki.reservation.slot.AvailableSlotEntity;
import com.azki.reservation.slot.AvailableSlotRepository;
import com.azki.reservation.user.UserEntity;
import com.azki.reservation.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.azki.reservation.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final AvailableSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    @CacheEvict(
            cacheNames = CacheConfig.AVAILABLE_SLOTS_CACHE,
            allEntries = true
    )
    @Transactional
    public ReservationResponse createReservation(
            Long slotId,
            Long userId
    ) {
        LocalDateTime now = LocalDateTime.now();

        int updatedRows = slotRepository.reserveIfAvailable(
                slotId,
                now
        );

        if (updatedRows == 0) {
            throwReservationError(slotId, now);
        }

        AvailableSlotEntity slot = slotRepository
                .findById(slotId)
                .orElseThrow(() ->
                        new SlotNotFoundException(slotId)
                );

        UserEntity user = userRepository.getReferenceById(userId);

        ReservationEntity reservation =
                new ReservationEntity(user, slot);

        ReservationEntity savedReservation =
                reservationRepository.save(reservation);

        return toResponse(savedReservation);
    }

    private void throwReservationError(
            Long slotId,
            LocalDateTime now
    ) {
        AvailableSlotEntity slot = slotRepository
                .findById(slotId)
                .orElseThrow(() ->
                        new SlotNotFoundException(slotId)
                );

        if (slot.isReserved()) {
            throw new SlotAlreadyReservedException(slotId);
        }

        if (slot.getStartTime().isBefore(now)) {
            throw new SlotUnavailableException(slotId);
        }

        throw new SlotUnavailableException(slotId);
    }

    private ReservationResponse toResponse(
            ReservationEntity reservation
    ) {
        AvailableSlotEntity slot = reservation.getSlot();

        return new ReservationResponse(
                reservation.getId(),
                slot.getId(),
                reservation.getUser().getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                reservation.getCreatedAt()
        );
    }



    @CacheEvict(
            cacheNames = CacheConfig.AVAILABLE_SLOTS_CACHE,
            allEntries = true
    )
    @Transactional
    public void cancelReservation(
            Long reservationId,
            Long userId
    ) {
        Long slotId = reservationRepository
                .findSlotIdByReservationIdAndUserId(
                        reservationId,
                        userId
                )
                .orElseThrow(() ->
                        new ReservationNotFoundException(
                                reservationId
                        )
                );

        int deletedRows = reservationRepository
                .deleteByReservationIdAndUserId(
                        reservationId,
                        userId
                );

        if (deletedRows == 0) {
            throw new ReservationNotFoundException(
                    reservationId
            );
        }

        int releasedRows = slotRepository
                .releaseReservedSlot(slotId);

        if (releasedRows == 0) {
            throw new ReservationStateException(
                    reservationId
            );
        }
    }
}