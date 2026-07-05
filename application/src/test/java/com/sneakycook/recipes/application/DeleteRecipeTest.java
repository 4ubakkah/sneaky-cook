package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteRecipeTest {

    @Mock
    private RecipeRepository recipes;

    @Test
    @DisplayName("[REQ-3] deletes an existing recipe owned by the caller")
    void deletesExistingRecipe() {
        UUID id = UUID.randomUUID();
        when(recipes.findByIdAndOwner(id, RecipeTestData.OWNER))
                .thenReturn(Optional.of(RecipeTestData.potatoGratin(id)));

        new DeleteRecipe(recipes).execute(RecipeTestData.OWNER, id);

        verify(recipes).deleteById(id);
    }

    @Test
    @DisplayName("[REQ-3][REQ-18] unknown or foreign id → RecipeNotFoundException, nothing deleted")
    void throwsNotFoundAndDeletesNothing() {
        UUID id = UUID.randomUUID();
        when(recipes.findByIdAndOwner(id, RecipeTestData.OWNER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecipeNotFoundException.class)
                .isThrownBy(() -> new DeleteRecipe(recipes).execute(RecipeTestData.OWNER, id));

        verify(recipes, never()).deleteById(any());
    }
}
