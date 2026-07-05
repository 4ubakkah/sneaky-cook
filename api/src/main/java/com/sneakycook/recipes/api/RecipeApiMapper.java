package com.sneakycook.recipes.api;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeSort;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generated mapping between the domain aggregate and the contract DTOs (spec
 * §3): no hand-written field copying anywhere, and a silently dropped field is
 * a compile error.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecipeApiMapper {

    com.sneakycook.recipes.api.generated.model.Recipe toApi(Recipe recipe);

    com.sneakycook.recipes.api.generated.model.RecipePage toApi(RecipePage page);

    /** Timestamps are stored as instants and exposed in UTC on the wire. */
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Assembles the domain filter from the raw query parameters [REQ-4..10]
     * scoped to the calling user [REQ-18]. The contract has already validated
     * shapes and ranges by the time this runs (pattern on {@code sort}, minima
     * on paging).
     */
    default RecipeFilter toFilter(
            UUID ownerId,
            Boolean vegetarian,
            Integer servings,
            List<String> includeIngredients,
            List<String> excludeIngredients,
            String instructionsContain,
            Integer page,
            Integer size,
            String sort) {
        return new RecipeFilter(
                ownerId,
                vegetarian,
                servings,
                includeIngredients,
                excludeIngredients,
                instructionsContain,
                page,
                size,
                toSort(sort, instructionsContain));
    }

    /**
     * {@code "servings,desc"} → typed sort; {@code null} → relevance when
     * {@code instructionsContain} is present, otherwise newest first.
     */
    default RecipeSort toSort(String sort, String instructionsContain) {
        if (sort == null) {
            if (instructionsContain != null && !instructionsContain.isBlank()) {
                return RecipeSort.RELEVANCE;
            }
            return RecipeSort.NEWEST_FIRST;
        }
        String[] parts = sort.split(",");
        RecipeSort.Field field = switch (parts[0]) {
            case "name" -> RecipeSort.Field.NAME;
            case "servings" -> RecipeSort.Field.SERVINGS;
            case "createdAt" -> RecipeSort.Field.CREATED_AT;
            default -> throw new IllegalArgumentException("Unsupported sort field: " + parts[0]);
        };
        return new RecipeSort(field, RecipeSort.Direction.valueOf(parts[1].toUpperCase(Locale.ROOT)));
    }
}
