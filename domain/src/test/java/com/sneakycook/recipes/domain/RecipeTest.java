package com.sneakycook.recipes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Invariants of the {@link Recipe} aggregate [REQ-2]: a constructed instance
 * is valid by definition, on every construction path.
 */
class RecipeTest {

    private static final Instant NOW = Instant.parse("2026-07-03T12:00:00.123456789Z");

    private static Recipe gratin() {
        return Recipe.createNew(
                "Potato gratin", true, 4,
                List.of("potatoes", "cream"), "Bake in the oven.", NOW);
    }

    @ParameterizedTest(name = "[REQ-2] blank name ''{0}'' is rejected")
    @ValueSource(strings = {"", "   ", "\t"})
    void rejectsBlankName(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recipe.createNew(name, true, 4, List.of("potatoes"), "Bake.", NOW))
                .withMessageContaining("name");
    }

    @Test
    @DisplayName("[REQ-2] zero servings is rejected")
    void rejectsZeroServings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recipe.createNew("Gratin", true, 0, List.of("potatoes"), "Bake.", NOW))
                .withMessageContaining("servings");
    }

    @Test
    @DisplayName("[REQ-2] an empty ingredient list is rejected")
    void rejectsEmptyIngredients() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recipe.createNew("Gratin", true, 4, List.of(), "Bake.", NOW))
                .withMessageContaining("ingredients");
    }

    @Test
    @DisplayName("[REQ-2] a blank ingredient entry is rejected")
    void rejectsBlankIngredient() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recipe.createNew("Gratin", true, 4, List.of("potatoes", "  "), "Bake.", NOW))
                .withMessageContaining("ingredient");
    }

    @Test
    @DisplayName("[REQ-2] blank instructions are rejected")
    void rejectsBlankInstructions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recipe.createNew("Gratin", true, 4, List.of("potatoes"), "   ", NOW))
                .withMessageContaining("instructions");
    }

    @Test
    @DisplayName("[REQ-7] ingredients are lower-cased on creation")
    void lowercasesIngredientsOnCreate() {
        Recipe recipe = Recipe.createNew(
                "Gratin", true, 4, List.of("Potatoes", "CREAM"), "Bake.", NOW);

        assertThat(recipe.ingredients()).containsExactly("potatoes", "cream");
    }

    @Test
    @DisplayName("[REQ-7] ingredients are lower-cased on update too — same construction path")
    void lowercasesIngredientsOnUpdate() {
        Recipe updated = gratin().updatedWith(
                "Gratin", true, 4, List.of("Leeks", "CHEESE"), "Bake longer.");

        assertThat(updated.ingredients()).containsExactly("leeks", "cheese");
    }

    @Test
    @DisplayName("update replaces fields but keeps id and createdAt")
    void updateKeepsIdentityAndCreationTimestamp() {
        Recipe original = gratin();

        Recipe updated = original.updatedWith(
                "Potato and leek gratin", false, 6, List.of("leeks"), "Roast.");

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.name()).isEqualTo("Potato and leek gratin");
        assertThat(updated.vegetarian()).isFalse();
        assertThat(updated.servings()).isEqualTo(6);
    }

    @Test
    @DisplayName("[REQ-2] createdAt is truncated to microseconds (timestamptz precision)")
    void truncatesCreatedAtToMicroseconds() {
        assertThat(gratin().createdAt()).isEqualTo(Instant.parse("2026-07-03T12:00:00.123456Z"));
    }

    @Test
    @DisplayName("update re-validates: an invalid update is rejected as a whole")
    void updateRevalidates() {
        Recipe original = gratin();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> original.updatedWith("Gratin", true, 0, List.of("potatoes"), "Bake."));
    }

    @Test
    @DisplayName("[REQ-2] id and createdAt are required")
    void rejectsMissingIdOrCreatedAt() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Recipe(null, "Gratin", true, 4, List.of("potatoes"), "Bake.", NOW));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Recipe(UUID.randomUUID(), "Gratin", true, 4, List.of("potatoes"), "Bake.", null));
    }
}
