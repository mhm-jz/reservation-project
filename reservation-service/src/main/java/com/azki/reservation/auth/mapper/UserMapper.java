package com.azki.reservation.auth.mapper;

import com.azki.reservation.auth.dto.CurrentUserResponse;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.user.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface UserMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "email", source = "email")
    UserResponse toUserResponse(UserEntity user);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "email", source = "email")
    CurrentUserResponse toCurrentUserResponse(UserEntity user);
}
