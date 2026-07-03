package com.sneakycook.recipes.api;

import com.sneakycook.recipes.api.generated.RecipesApi;
import com.sneakycook.recipes.api.generated.model.Recipe;
import com.sneakycook.recipes.api.generated.model.RecipeRequest;
import com.sneakycook.recipes.application.CreateRecipe;
import com.sneakycook.recipes.application.GetRecipe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP edge for recipe management [REQ-1]. Implements the interfaces generated
 * from {@code recipe-api.yaml}; operations not yet backed by a use case
 * inherit the generated 501 Not Implemented default and turn real as the
 * corresponding build-order step lands (steps 4-6).
 */
@RestController
public class RecipesController implements RecipesApi {

    private final CreateRecipe createRecipe;
    private final GetRecipe getRecipe;
    private final RecipeApiMapper mapper;

    public RecipesController(CreateRecipe createRecipe, GetRecipe getRecipe, RecipeApiMapper mapper) {
        this.createRecipe = createRecipe;
        this.getRecipe = getRecipe;
        this.mapper = mapper;
    }

    /** [REQ-2] 201 with the created payload and a Location header. */
    @Override
    public ResponseEntity<Recipe> createRecipe(RecipeRequest recipeRequest) {
        var created = createRecipe.execute(
                recipeRequest.getName(),
                recipeRequest.getVegetarian(),
                recipeRequest.getServings(),
                recipeRequest.getIngredients(),
                recipeRequest.getInstructions());
        return ResponseEntity
                .created(URI.create("/api/v1/recipes/" + created.id()))
                .body(mapper.toApi(created));
    }

    /** [REQ-4] 200 with the recipe, or 404 via {@code RecipeNotFoundException}. */
    @Override
    public ResponseEntity<Recipe> getRecipe(UUID id) {
        return ResponseEntity.ok(mapper.toApi(getRecipe.execute(id)));
    }
}
