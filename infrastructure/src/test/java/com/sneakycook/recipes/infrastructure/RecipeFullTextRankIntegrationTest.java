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
    @DisplayName("[REQ-9] ts_rank orders by term frequency — more mentions rank higher")
    void termFrequencyIncreasesRank() {
        recipes.save(DomainRecipes.recipe(
                "Single quokka",
                true,
                2,
                List.of("stock"),
                "Fold in the quokka once at the end."));
        recipes.save(DomainRecipes.recipe(
                "Repeated quokka",
                true,
                2,
                List.of("stock"),
                "Quokka quokka quokka — keep folding quokka through the sauce."));

        RecipePage result = recipes.search(new RecipeFilter(
                DomainRecipes.OWNER, null, null, List.of(), List.of(), "quokka", 0, 20, RecipeSort.RELEVANCE));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst().name()).isEqualTo("Repeated quokka");
        assertThat(result.content().getLast().name()).isEqualTo("Single quokka");
    }

    @Test
    @DisplayName("[REQ-9] multi-term AND query ranks the document with stronger combined coverage higher")
    void multiTermQueryPrefersStrongerCombinedMatch() {
        recipes.save(DomainRecipes.recipe(
                "Light oven crisp",
                true,
                2,
                List.of("potatoes"),
                "Bake in the oven until crisp."));
        recipes.save(DomainRecipes.recipe(
                "Heavy oven crisp",
                true,
                2,
                List.of("potatoes"),
                "Crisp the base in the oven. Keep crisping in the oven until extra crisp."));

        RecipePage result = recipes.search(new RecipeFilter(
                DomainRecipes.OWNER, null, null, List.of(), List.of(), "oven crisp", 0, 20, RecipeSort.RELEVANCE));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst().name()).isEqualTo("Heavy oven crisp");
        assertThat(result.content().getLast().name()).isEqualTo("Light oven crisp");
    }

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
                DomainRecipes.OWNER, null, null, List.of(), List.of(), "oven", 0, 20, RecipeSort.RELEVANCE));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst().name()).isEqualTo("Heavy oven mention");
        assertThat(result.content().getLast().name()).isEqualTo("Light oven mention");
    }
}
