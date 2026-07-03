package com.sneakycook.recipes.api;

import com.sneakycook.recipes.api.generated.RecipesApi;
import com.sneakycook.recipes.api.generated.model.Recipe;
import com.sneakycook.recipes.api.generated.model.RecipePage;
import com.sneakycook.recipes.api.generated.model.RecipeRequest;
import com.sneakycook.recipes.application.CreateRecipe;
import com.sneakycook.recipes.application.DeleteRecipe;
import com.sneakycook.recipes.application.GetRecipe;
import com.sneakycook.recipes.application.ListRecipes;
import com.sneakycook.recipes.application.UpdateRecipe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * HTTP edge for recipe management [REQ-1]. Implements the interfaces generated
 * from {@code recipe-api.yaml}; each operation delegates to exactly one use
 * case and maps through the generated {@link RecipeApiMapper}.
 */
@RestController
public class RecipesController implements RecipesApi {

    private final CreateRecipe createRecipe;
    private final GetRecipe getRecipe;
    private final UpdateRecipe updateRecipe;
    private final DeleteRecipe deleteRecipe;
    private final ListRecipes listRecipes;
    private final RecipeApiMapper mapper;

    public RecipesController(
            CreateRecipe createRecipe,
            GetRecipe getRecipe,
            UpdateRecipe updateRecipe,
            DeleteRecipe deleteRecipe,
            ListRecipes listRecipes,
            RecipeApiMapper mapper) {
        this.createRecipe = createRecipe;
        this.getRecipe = getRecipe;
        this.updateRecipe = updateRecipe;
        this.deleteRecipe = deleteRecipe;
        this.listRecipes = listRecipes;
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

    /** Full replacement; id and createdAt are immutable (deliberate PUT extension, spec §4). */
    @Override
    public ResponseEntity<Recipe> updateRecipe(UUID id, RecipeRequest recipeRequest) {
        var updated = updateRecipe.execute(
                id,
                recipeRequest.getName(),
                recipeRequest.getVegetarian(),
                recipeRequest.getServings(),
                recipeRequest.getIngredients(),
                recipeRequest.getInstructions());
        return ResponseEntity.ok(mapper.toApi(updated));
    }

    /** [REQ-3] 204 on success, 404 for an unknown or already-deleted id. */
    @Override
    public ResponseEntity<Void> deleteRecipe(UUID id) {
        deleteRecipe.execute(id);
        return ResponseEntity.noContent().build();
    }

    /** [REQ-4..REQ-10] Paged list; all filter criteria optional and combinable. */
    @Override
    public ResponseEntity<RecipePage> listRecipes(
            Boolean vegetarian,
            Integer servings,
            List<String> includeIngredients,
            List<String> excludeIngredients,
            String instructionsContain,
            Integer page,
            Integer size,
            String sort) {
        var filter = mapper.toFilter(
                vegetarian, servings, includeIngredients, excludeIngredients,
                instructionsContain, page, size, sort);
        return ResponseEntity.ok(mapper.toApi(listRecipes.execute(filter)));
    }
}
