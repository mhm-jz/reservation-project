package com.azki.reservation.slot.mapper;

import com.azki.reservation.slot.AvailableSlotEntity;
import com.azki.reservation.slot.dto.AvailableSlotResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface SlotMapper {

    AvailableSlotResponse toResponse(AvailableSlotEntity slot);

    List<AvailableSlotResponse> toResponses(
            List<AvailableSlotEntity> slots
    );
}
