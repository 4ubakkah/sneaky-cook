package com.sneakycook.recipes.api;

import com.sneakycook.recipes.api.generated.model.UserResponse;
import com.sneakycook.recipes.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Generated mapping between the user aggregate and the contract DTOs (spec
 * §3). {@code passwordHash} has no target field in {@code UserResponse} by
 * design — the hash can never leak onto the wire.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthApiMapper {

    UserResponse toApi(User user);

    /** Timestamps are stored as instants and exposed in UTC on the wire. */
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
