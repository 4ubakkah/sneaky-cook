package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Generated mapping between the domain aggregate and its JPA entity — no
 * hand-written field copying, and {@code unmappedTargetPolicy = ERROR} makes a
 * silently dropped field a compile error.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecipeEntityMapper {

    @Mapping(target = "instructionsTsv", ignore = true)
    RecipeEntity toEntity(Recipe recipe);

    Recipe toDomain(RecipeEntity entity);
}
