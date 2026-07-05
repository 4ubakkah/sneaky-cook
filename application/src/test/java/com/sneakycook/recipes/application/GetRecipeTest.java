package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRecipeTest {

    @Mock
    private RecipeRepository recipes;

    @Test
    @DisplayName("[REQ-4] returns the recipe when it exists and belongs to the caller")
    void returnsExistingRecipe() {
        UUID id = UUID.randomUUID();
        Recipe gratin = RecipeTestData.potatoGratin(id);
        when(recipes.findByIdAndOwner(id, RecipeTestData.OWNER)).thenReturn(Optional.of(gratin));

        assertThat(new GetRecipe(recipes).execute(RecipeTestData.OWNER, id)).isEqualTo(gratin);
    }

    @Test
    @DisplayName("[REQ-4][REQ-18] unknown or foreign id → RecipeNotFoundException naming the id")
    void throwsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(recipes.findByIdAndOwner(id, RecipeTestData.OWNER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecipeNotFoundException.class)
                .isThrownBy(() -> new GetRecipe(recipes).execute(RecipeTestData.OWNER, id))
                .withMessageContaining(id.toString());
    }
}
