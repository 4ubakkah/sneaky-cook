package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRecipeTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-03T12:00:00.123456789Z");

    @Mock
    private RecipeRepository recipes;

    @Test
    @DisplayName("[REQ-2] persists a recipe stamped with the clock's instant and returns the saved aggregate")
    void persistsWithServerManagedTimestamp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        when(recipes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Recipe created = new CreateRecipe(recipes, fixedClock).execute(
                "Potato gratin", true, 4, List.of("potatoes", "cream"), "Bake in the oven.");

        ArgumentCaptor<Recipe> saved = ArgumentCaptor.forClass(Recipe.class);
        verify(recipes).save(saved.capture());
        assertThat(saved.getValue().createdAt())
                .isEqualTo(Instant.parse("2026-07-03T12:00:00.123456Z")); // micros precision
        assertThat(created).isEqualTo(saved.getValue());
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Potato gratin");
    }
}
