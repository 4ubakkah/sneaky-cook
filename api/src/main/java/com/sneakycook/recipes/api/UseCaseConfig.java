package com.sneakycook.recipes.api;

import com.sneakycook.recipes.application.AuthenticateUser;
import com.sneakycook.recipes.application.CreateRecipe;
import com.sneakycook.recipes.application.DeleteRecipe;
import com.sneakycook.recipes.application.GetRecipe;
import com.sneakycook.recipes.application.ListRecipes;
import com.sneakycook.recipes.application.RegisterUser;
import com.sneakycook.recipes.application.UpdateRecipe;
import com.sneakycook.recipes.domain.PasswordHasher;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the Spring-free use cases as beans. The application module carries no
 * Spring annotations (spec §3), so assembly happens here at the edge.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CreateRecipe createRecipe(RecipeRepository recipes, Clock clock) {
        return new CreateRecipe(recipes, clock);
    }

    @Bean
    GetRecipe getRecipe(RecipeRepository recipes) {
        return new GetRecipe(recipes);
    }

    @Bean
    UpdateRecipe updateRecipe(RecipeRepository recipes) {
        return new UpdateRecipe(recipes);
    }

    @Bean
    DeleteRecipe deleteRecipe(RecipeRepository recipes) {
        return new DeleteRecipe(recipes);
    }

    @Bean
    ListRecipes listRecipes(RecipeRepository recipes) {
        return new ListRecipes(recipes);
    }

    @Bean
    RegisterUser registerUser(UserRepository users, PasswordHasher hasher, Clock clock) {
        return new RegisterUser(users, hasher, clock);
    }

    @Bean
    AuthenticateUser authenticateUser(UserRepository users, PasswordHasher hasher) {
        return new AuthenticateUser(users, hasher);
    }
}
