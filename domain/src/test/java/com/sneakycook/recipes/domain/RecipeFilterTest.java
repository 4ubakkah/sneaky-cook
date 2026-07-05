package com.sneakycook.recipes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The filter value object [REQ-5..REQ-10] normalizes and defaults consistently. */
class RecipeFilterTest {

    private static final UUID OWNER = UUID.fromString("7b1e8a90-3c2d-4f6e-9a1b-2c3d4e5f6a7b");

    @Test
    @DisplayName("[REQ-7][REQ-8] ingredient criteria are lower-cased to match stored normalization")
    void lowercasesIngredientCriteria() {
        RecipeFilter filter = new RecipeFilter(
                OWNER, null, null, List.of("Potatoes"), List.of("SALMON"), null, 0, 20, null);

        assertThat(filter.includeIngredients()).containsExactly("potatoes");
        assertThat(filter.excludeIngredients()).containsExactly("salmon");
    }

    @Test
    @DisplayName("null ingredient criteria become empty lists — no null checks downstream")
    void nullCriteriaBecomeEmptyLists() {
        RecipeFilter filter = new RecipeFilter(OWNER, null, null, null, null, null, 0, 20, null);

        assertThat(filter.includeIngredients()).isEmpty();
        assertThat(filter.excludeIngredients()).isEmpty();
    }

    @Test
    @DisplayName("[REQ-4] missing sort defaults to newest first (createdAt desc)")
    void defaultsToNewestFirst() {
        RecipeFilter filter = new RecipeFilter(OWNER, null, null, null, null, null, 0, 20, null);

        assertThat(filter.sort()).isEqualTo(RecipeSort.NEWEST_FIRST);
    }

    @Test
    @DisplayName("[REQ-4] negative page is rejected")
    void rejectsNegativePage() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecipeFilter(OWNER, null, null, null, null, null, -1, 20, null));
    }

    @Test
    @DisplayName("[REQ-4] zero size is rejected")
    void rejectsZeroSize() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecipeFilter(OWNER, null, null, null, null, null, 0, 0, null));
    }

    @Test
    @DisplayName("[REQ-18] missing ownerId is rejected — every search is owner-scoped")
    void rejectsMissingOwner() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecipeFilter(null, null, null, null, null, null, 0, 20, null))
                .withMessageContaining("ownerId");
    }
}
