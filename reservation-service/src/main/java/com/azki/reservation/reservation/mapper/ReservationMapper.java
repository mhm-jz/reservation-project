package com.azki.reservation.reservation.mapper;

import com.azki.reservation.reservation.entity.ReservationEntity;
import com.azki.reservation.reservation.dto.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ReservationMapper {

    @Mapping(target = "slotId", source = "slot.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "startTime", source = "slot.startTime")
    @Mapping(target = "endTime", source = "slot.endTime")
    ReservationResponse toResponse(ReservationEntity reservation);
}
