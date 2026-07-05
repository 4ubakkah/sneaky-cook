package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.RecipeSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced full-text search behaviour on real PostgreSQL (spec §5, §7, caveat 1):
 * word-based matching via {@code websearch_to_tsquery}, generated {@code
 * instructions_tsv}, and composable JPA Specifications — failure localization
 * the HTTP tier cannot give without brittle rank assertions across five fixtures.
 */
class RecipeFullTextSearchIntegrationTest extends PostgresDataJpaTestBase {

    @Autowired
    private RecipeRepository recipes;

    @BeforeEach
    void seedStandardFixtures() {
        DomainRecipes.allFive().forEach(recipes::save);
    }

    @Nested
    @DisplayName("word-based matching (not substring)")
    class WordBasedMatching {

        @Test
        @DisplayName("[REQ-9] partial prefix 'ove' does not match 'oven' — unlike ILIKE substring search")
        void partialPrefixDoesNotMatch() {
            RecipePage result = search("ove");

            assertThat(names(result)).isEmpty();
        }

        @Test
        @DisplayName("[REQ-9] search is case-insensitive on the indexed lexemes")
        void searchIsCaseInsensitive() {
            assertThat(names(search("OVEN")))
                    .containsExactlyInAnyOrder("Potato gratin", "Salmon traybake");
        }

        @Test
        @DisplayName("[REQ-9] English stop words in the query do not change the match set")
        void stopWordsInQueryDoNotRestrict() {
            assertThat(names(search("the oven"))).containsExactlyInAnyOrder(
                    "Potato gratin", "Salmon traybake");
            assertThat(names(search("oven"))).containsExactlyInAnyOrder(
                    "Potato gratin", "Salmon traybake");
        }

        @Test
        @DisplayName("[REQ-9] only instructions are indexed — ingredient names are not searched")
        void ingredientNamesAreNotIndexed() {
            recipes.save(DomainRecipes.recipe(
                    "Microwave ingredient only",
                    true,
                    2,
                    List.of("microwave"),
                    "Heat gently on the stove until warm."));

            assertThat(names(search("microwave"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("English stemming")
    class Stemming {

        @Test
        @DisplayName("[REQ-9] 'baking' stems to match stored 'Bake'")
        void bakingStemMatchesBake() {
            assertThat(names(search("baking"))).containsExactly("Potato gratin");
        }

        @Test
        @DisplayName("[REQ-9] 'simmered' stems to match stored 'simmer'")
        void simmeredStemMatchesSimmer() {
            assertThat(names(search("simmered")))
                    .containsExactlyInAnyOrder("Beef stew", "Vegetable soup");
        }
    }

    @Nested
    @DisplayName("websearch_to_tsquery query syntax")
    class WebsearchSyntax {

        @Test
        @DisplayName("[REQ-9] space-separated words are AND-ed — 'bake roast' matches neither fixture")
        void multiWordAndRequiresEveryTerm() {
            assertThat(names(search("bake roast"))).isEmpty();
        }

        @Test
        @DisplayName("[REQ-9] 'or' keyword matches recipes containing either term")
        void orKeywordMatchesEitherTerm() {
            assertThat(names(search("bake or roast")))
                    .containsExactlyInAnyOrder("Potato gratin", "Salmon traybake");
        }

        @Test
        @DisplayName("[REQ-9] negation excludes documents containing the stemmed term")
        void negationExcludesMatchingDocuments() {
            assertThat(names(search("oven -roast"))).containsExactly("Potato gratin");
        }

        @Test
        @DisplayName("[REQ-9] quoted phrase requires adjacent lexemes — 'bake oven' phrase ≠ unquoted AND")
        void quotedPhraseRequiresAdjacentLexemes() {
            // Unquoted AND matches gratin ("Bake … oven").
            assertThat(names(search("bake oven"))).containsExactly("Potato gratin");
            // Phrase "bake oven" requires the tokens next to each other in the tsvector.
            assertThat(names(search("\"bake oven\""))).isEmpty();
        }

        @Test
        @DisplayName("[REQ-9] quoted phrase matches when the words appear consecutively in instructions")
        void quotedPhraseMatchesConsecutiveWords() {
            recipes.save(DomainRecipes.recipe(
                    "Phrase match",
                    true,
                    2,
                    List.of("flour"),
                    "Fold in the flibble plop mixture and rest."));
            recipes.save(DomainRecipes.recipe(
                    "Scattered words",
                    true,
                    2,
                    List.of("flour"),
                    "Fold in the flibble. Later add plop and rest."));

            assertThat(names(search("\"flibble plop\""))).containsExactly("Phrase match");
        }
    }

    @Nested
    @DisplayName("generated tsvector column")
    class GeneratedColumn {

        @Test
        @DisplayName("[REQ-9] instructions_tsv refreshes after UPDATE — new terms become searchable")
        void updatedInstructionsBecomeSearchable() {
            Recipe saved = recipes.save(DomainRecipes.recipe(
                    "Stovetop oats",
                    true,
                    1,
                    List.of("oats"),
                    "Simmer the oats on the stove until creamy."));

            assertThat(names(search("microwave"))).isEmpty();

            Recipe updated = saved.updatedWith(
                    saved.name(),
                    saved.vegetarian(),
                    saved.servings(),
                    saved.ingredients(),
                    "Finish in the microwave until the oats puff.");
            recipes.save(updated);

            assertThat(names(search("microwave"))).containsExactly("Stovetop oats");
        }
    }

    @Nested
    @DisplayName("filter composition and paging")
    class CompositionAndPaging {

        @Test
        @DisplayName("[REQ-9] blank instructionsContain is ignored — no full-text predicate applied")
        void blankQueryIsIgnored() {
            RecipePage result = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, null, null, List.of(), List.of(), "   ", 0, 20, RecipeSort.NEWEST_FIRST));

            assertThat(result.totalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("[REQ-4] page envelope reflects the full-text-filtered total, not the table size")
        void pagingReflectsFilteredTotal() {
            RecipePage result = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, null, null, List.of(), List.of(), "oven", 0, 1, RecipeSort.RELEVANCE));

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("[REQ-9] explicit sort bypasses relevance ranking from fts_rank")
        void explicitSortBypassesRelevance() {
            Recipe light = recipes.save(DomainRecipes.recipe(
                    "A light match",
                    true,
                    2,
                    List.of("potatoes"),
                    "Add the quokka once and bake until done."));
            Recipe heavy = recipes.save(DomainRecipes.recipe(
                    "Z heavy match",
                    true,
                    2,
                    List.of("potatoes"),
                    "Quokka quokka quokka — fold in repeatedly until glossy."));

            RecipePage byRelevance = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, null, null, List.of(), List.of(), "quokka", 0, 20, RecipeSort.RELEVANCE));
            assertThat(names(byRelevance)).startsWith("Z heavy match");

            RecipePage byName = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, null, null, List.of(), List.of(), "quokka", 0, 20,
                    new RecipeSort(RecipeSort.Field.NAME, RecipeSort.Direction.ASC)));
            assertThat(names(byName)).startsWith("A light match");
        }

        @Test
        @DisplayName("[REQ-10] all structural filters compose with full-text search and explicit name sort")
        void allStructuralFiltersWithFullTextAndExplicitSort() {
            RecipePage result = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER,
                    true,
                    4,
                    List.of("potatoes"),
                    List.of("salmon"),
                    "oven",
                    0,
                    20,
                    new RecipeSort(RecipeSort.Field.NAME, RecipeSort.Direction.ASC)));

            assertThat(names(result)).containsExactly("Potato gratin");
        }

        @Test
        @DisplayName("[REQ-8][REQ-9] exclusion removes full-text hits that contain the excluded ingredient")
        void exclusionNarrowsFullTextResults() {
            RecipePage result = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, null, null, List.of(), List.of("salmon"), "oven", 0, 20, RecipeSort.RELEVANCE));

            assertThat(names(result)).containsExactly("Potato gratin");
        }

        @Test
        @DisplayName("[REQ-5][REQ-9] vegetarian flag narrows full-text matches")
        void vegetarianNarrowsFullTextResults() {
            RecipePage result = recipes.search(new RecipeFilter(
                    DomainRecipes.OWNER, true, null, List.of(), List.of(), "oven", 0, 20, RecipeSort.RELEVANCE));

            assertThat(names(result)).containsExactly("Potato gratin");
        }
    }

    private RecipePage search(String instructionsContain) {
        return recipes.search(new RecipeFilter(
                DomainRecipes.OWNER, null, null, List.of(), List.of(), instructionsContain, 0, 20, RecipeSort.RELEVANCE));
    }

    private static List<String> names(RecipePage page) {
        return page.content().stream().map(Recipe::name).toList();
    }
}
