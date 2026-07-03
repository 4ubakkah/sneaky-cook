package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filter predicates in isolation (spec §7): failure localization the E2E tier
 * can't give. Focuses on predicate interaction and SQL-level behaviour; the
 * HTTP-visible behaviour of the same rules lives in the E2E tier.
 */
class RecipeSpecificationsIntegrationTest extends PostgresDataJpaTestBase {

    @Autowired
    private RecipeRepository recipes;

    @BeforeEach
    void seedAllFiveRecipes() {
        DomainRecipes.allFive().forEach(recipes::save);
    }

    @Test
    @DisplayName("[REQ-7][REQ-8] include and exclude interact: with potatoes but without salmon")
    void includeAndExcludeInteraction() {
        RecipePage result = recipes.search(filter()
                .includeIngredients(List.of("potatoes"))
                .excludeIngredients(List.of("salmon"))
                .build());

        assertThat(names(result)).containsExactlyInAnyOrder("Potato gratin", "Vegetable soup");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("[REQ-7] multiple includes are AND: only the recipe containing every listed ingredient matches")
    void multipleIncludesAreAndSemantics() {
        RecipePage result = recipes.search(filter()
                .includeIngredients(List.of("potatoes", "carrots"))
                .build());

        assertThat(names(result)).containsExactly("Vegetable soup");
    }

    @Test
    @DisplayName("[REQ-8] multiple excludes are NONE: one offending ingredient is enough to reject")
    void multipleExcludesAreNoneSemantics() {
        RecipePage result = recipes.search(filter()
                .excludeIngredients(List.of("salmon", "beef"))
                .build());

        assertThat(names(result))
                .containsExactlyInAnyOrder("Potato gratin", "Mushroom risotto", "Vegetable soup");
    }

    @Test
    @DisplayName("[REQ-8] excluding an ingredient present in every fixture with it leaves the rest untouched")
    void exclusionDoesNotOverReject() {
        RecipePage result = recipes.search(filter()
                .excludeIngredients(List.of("potatoes"))
                .build());

        assertThat(names(result)).containsExactlyInAnyOrder("Mushroom risotto", "Beef stew");
    }

    @Test
    @DisplayName("[REQ-10] all four structural criteria compose into one query")
    void allStructuralCriteriaCompose() {
        RecipePage result = recipes.search(filter()
                .vegetarian(true)
                .servings(4)
                .includeIngredients(List.of("potatoes"))
                .excludeIngredients(List.of("salmon"))
                .build());

        assertThat(names(result)).containsExactlyInAnyOrder("Potato gratin", "Vegetable soup");
    }

    @Test
    @DisplayName("[REQ-4] page envelope numbers reflect the filtered totals, not the table size")
    void pageEnvelopeReflectsFilteredTotals() {
        RecipePage result = recipes.search(filter()
                .vegetarian(true)
                .size(2)
                .build());

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    private static List<String> names(RecipePage page) {
        return page.content().stream().map(Recipe::name).toList();
    }

    private static FilterBuilder filter() {
        return new FilterBuilder();
    }

    /** Small builder so each test states only the criteria it is about. */
    private static final class FilterBuilder {
        private Boolean vegetarian;
        private Integer servings;
        private List<String> include = List.of();
        private List<String> exclude = List.of();
        private int size = 20;

        FilterBuilder vegetarian(boolean value) {
            this.vegetarian = value;
            return this;
        }

        FilterBuilder servings(int value) {
            this.servings = value;
            return this;
        }

        FilterBuilder includeIngredients(List<String> value) {
            this.include = value;
            return this;
        }

        FilterBuilder excludeIngredients(List<String> value) {
            this.exclude = value;
            return this;
        }

        FilterBuilder size(int value) {
            this.size = value;
            return this;
        }

        RecipeFilter build() {
            return new RecipeFilter(vegetarian, servings, include, exclude, null, 0, size, null);
        }
    }
}
