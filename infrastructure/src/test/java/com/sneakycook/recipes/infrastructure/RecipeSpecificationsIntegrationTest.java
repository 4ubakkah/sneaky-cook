package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.RecipeSort;
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

    @Test
    @DisplayName("[REQ-9] instructionsContain matches only recipes whose instructions mention the term")
    void instructionsContainMatchesOven() {
        RecipePage result = recipes.search(filter()
                .instructionsContain("oven")
                .build());

        assertThat(names(result)).containsExactlyInAnyOrder("Potato gratin", "Salmon traybake");
    }

    @Test
    @DisplayName("[REQ-9] instruction search is word-stemmed: 'roasting' matches 'Roast'")
    void instructionSearchIsStemmed() {
        RecipePage result = recipes.search(filter()
                .instructionsContain("roasting")
                .build());

        assertThat(names(result)).containsExactly("Salmon traybake");
    }

    @Test
    @DisplayName("[REQ-9] multi-word instruction search requires all words via websearch_to_tsquery")
    void multiWordInstructionSearchRequiresAllWords() {
        RecipePage result = recipes.search(filter()
                .instructionsContain("bake oven")
                .build());

        assertThat(names(result)).containsExactly("Potato gratin");
    }

    @Test
    @DisplayName("[REQ-18] search never returns another user's recipes, and totals are per-owner")
    void searchIsOwnerScoped() {
        recipes.save(DomainRecipes.recipeOwnedBy(
                DomainRecipes.OTHER_OWNER,
                "Foreign potato gratin", true, 4,
                List.of("potatoes", "cream"),
                "Layer and bake in the oven until golden."));

        RecipePage result = recipes.search(filter()
                .includeIngredients(List.of("potatoes"))
                .build());

        assertThat(names(result)).containsExactlyInAnyOrder(
                "Potato gratin", "Salmon traybake", "Vegetable soup");
        assertThat(names(result)).doesNotContain("Foreign potato gratin");
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("[REQ-18] findByIdAndOwner hides foreign recipes — indistinguishable from missing")
    void findByIdAndOwnerHidesForeignRecipes() {
        var foreign = recipes.save(DomainRecipes.recipeOwnedBy(
                DomainRecipes.OTHER_OWNER,
                "Foreign stew", false, 6,
                List.of("beef"),
                "Simmer gently on the hob for hours."));

        assertThat(recipes.findByIdAndOwner(foreign.id(), DomainRecipes.OWNER)).isEmpty();
        assertThat(recipes.findByIdAndOwner(foreign.id(), DomainRecipes.OTHER_OWNER)).isPresent();
    }

    @Test
    @DisplayName("[REQ-10] structural filters compose with full-text search")
    void structuralFiltersComposeWithFullTextSearch() {
        RecipePage result = recipes.search(filter()
                .vegetarian(true)
                .servings(4)
                .includeIngredients(List.of("potatoes"))
                .excludeIngredients(List.of("salmon"))
                .instructionsContain("oven")
                .build());

        assertThat(names(result)).containsExactly("Potato gratin");
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
        private String instructionsContain;
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

        FilterBuilder instructionsContain(String value) {
            this.instructionsContain = value;
            return this;
        }

        FilterBuilder size(int value) {
            this.size = value;
            return this;
        }

        RecipeFilter build() {
            return new RecipeFilter(
                    DomainRecipes.OWNER,
                    vegetarian, servings, include, exclude, instructionsContain, 0, size,
                    instructionsContain != null && !instructionsContain.isBlank()
                            ? RecipeSort.RELEVANCE
                            : null);
        }
    }
}
