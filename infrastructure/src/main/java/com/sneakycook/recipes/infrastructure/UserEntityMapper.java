package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Generated mapping between the user aggregate and its JPA entity (spec §3). */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserEntityMapper {

    UserEntity toEntity(User user);

    User toDomain(UserEntity entity);
}
