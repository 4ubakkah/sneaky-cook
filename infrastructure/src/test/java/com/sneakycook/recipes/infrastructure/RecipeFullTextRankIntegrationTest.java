package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.RecipeSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-text rank ordering in isolation (spec §7): deterministic fixtures, not
 * asserted in E2E because tie-breaking across five recipes is fragile.
 */
class RecipeFullTextRankIntegrationTest extends PostgresDataJpaTestBase {

    @Autowired
    private RecipeRepository recipes;

    @Test
    @DisplayName("[REQ-9] full-text rank orders the strongest match first")
    void fullTextRankOrdersStrongestMatchFirst() {
        recipes.save(DomainRecipes.recipe(
                "Light oven mention",
                true,
                2,
                List.of("potatoes"),
                "Bake in the oven until done."));
        recipes.save(DomainRecipes.recipe(
                "Heavy oven mention",
                true,
                2,
                List.of("potatoes"),
                "Preheat the oven. Bake in the oven. Return to the oven until deeply golden."));

        RecipePage result = recipes.search(new RecipeFilter(
                null, null, List.of(), List.of(), "oven", 0, 20, RecipeSort.RELEVANCE));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst().name()).isEqualTo("Heavy oven mention");
        assertThat(result.content().getLast().name()).isEqualTo("Light oven mention");
    }
}
