package com.sneakycook.recipes.api;

import com.sneakycook.recipes.api.generated.RecipesApi;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP edge for recipe management [REQ-1]. Implements the interfaces generated
 * from {@code recipe-api.yaml}; every operation currently inherits the
 * generated 501 Not Implemented default and turns real as the corresponding
 * use case lands (build-order steps 3-6).
 */
@RestController
public class RecipesController implements RecipesApi {
}
