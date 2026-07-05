package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.RecipeSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRecipesTest {

    @Mock
    private RecipeRepository recipes;

    @Test
    @DisplayName("[REQ-4] delegates the filter to the repository search port")
    void delegatesToRepositorySearch() {
        RecipeFilter filter = new RecipeFilter(
                RecipeTestData.OWNER,
                true, 4, List.of("potatoes"), List.of("salmon"), "oven", 0, 20, RecipeSort.RELEVANCE);
        RecipePage expected = new RecipePage(List.of(), 0, 20, 0, 0);
        when(recipes.search(filter)).thenReturn(expected);

        assertThat(new ListRecipes(recipes).execute(filter)).isEqualTo(expected);
    }
}
