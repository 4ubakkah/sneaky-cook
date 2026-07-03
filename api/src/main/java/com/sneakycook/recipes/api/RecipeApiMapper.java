package com.sneakycook.recipes.api;

import com.sneakycook.recipes.domain.Recipe;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Generated mapping between the domain aggregate and the contract DTOs (spec
 * §3): no hand-written field copying anywhere, and a silently dropped field is
 * a compile error.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecipeApiMapper {

    com.sneakycook.recipes.api.generated.model.Recipe toApi(Recipe recipe);

    /** Timestamps are stored as instants and exposed in UTC on the wire. */
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
