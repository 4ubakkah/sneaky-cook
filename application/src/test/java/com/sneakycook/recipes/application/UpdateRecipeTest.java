package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateRecipeTest {

    @Mock
    private RecipeRepository recipes;

    @Test
    @DisplayName("replaces all client-writable fields but keeps id and createdAt")
    void replacesFieldsKeepsIdentity() {
        UUID id = UUID.randomUUID();
        when(recipes.findById(id)).thenReturn(Optional.of(RecipeTestData.potatoGratin(id)));
        when(recipes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new UpdateRecipe(recipes).execute(
                id, "Potato and leek gratin", false, 6, List.of("potatoes", "leeks"), "Roast it all.");

        ArgumentCaptor<Recipe> saved = ArgumentCaptor.forClass(Recipe.class);
        verify(recipes).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().createdAt()).isEqualTo(RecipeTestData.CREATED_AT);
        assertThat(saved.getValue().name()).isEqualTo("Potato and leek gratin");
        assertThat(saved.getValue().vegetarian()).isFalse();
        assertThat(saved.getValue().servings()).isEqualTo(6);
        assertThat(saved.getValue().ingredients()).containsExactly("potatoes", "leeks");
    }

    @Test
    @DisplayName("unknown id → RecipeNotFoundException, nothing saved")
    void throwsNotFoundAndSavesNothing() {
        UUID id = UUID.randomUUID();
        when(recipes.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecipeNotFoundException.class)
                .isThrownBy(() -> new UpdateRecipe(recipes).execute(
                        id, "Name", true, 2, List.of("rice"), "Cook."));

        verify(recipes, never()).save(any());
    }
}
