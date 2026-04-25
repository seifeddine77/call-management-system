package com.pfe.callmgmt.user.mapper;

import com.pfe.callmgmt.user.dto.AgenceResponse;
import com.pfe.callmgmt.user.entity.Agence;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgenceMapper {

    AgenceResponse toResponse(Agence agence);
}
