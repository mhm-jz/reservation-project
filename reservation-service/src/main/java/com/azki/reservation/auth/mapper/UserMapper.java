package com.azki.reservation.auth.mapper;

import com.azki.reservation.auth.dto.CurrentUserResponse;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.user.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface UserMapper {

    UserResponse toUserResponse(UserEntity user);

    CurrentUserResponse toCurrentUserResponse(UserEntity user);
}
