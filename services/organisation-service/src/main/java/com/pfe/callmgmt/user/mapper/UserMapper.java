package com.pfe.callmgmt.user.mapper;

import com.pfe.callmgmt.user.dto.UserResponse;
import com.pfe.callmgmt.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "agence.id", target = "agenceId")
    @Mapping(source = "agence.nom", target = "agenceName")
    UserResponse toResponse(User user);
}
