package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-level behaviour [REQ-3]: ingredient rows go with their recipe via the
 * FK's ON DELETE CASCADE — deliberately exercised with raw SQL so the guarantee
 * holds even for deletes that bypass Hibernate (not observable through the
 * API, spec §7).
 */
class IngredientCascadeIntegrationTest extends PostgresDataJpaTestBase {

    @Autowired
    private RecipeRepository recipes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("[REQ-3] deleting a recipe row cascades to its ingredient rows")
    void deletingRecipeRowCascadesToIngredients() {
        Recipe gratin = recipes.save(DomainRecipes.potatoGratin());
        entityManager.flush(); // raw SQL below must see the pending inserts
        assertThat(countIngredients(gratin)).isEqualTo(4);

        jdbc.update("DELETE FROM recipe WHERE id = ?", gratin.id());

        assertThat(countIngredients(gratin)).isZero();
    }

    private Integer countIngredients(Recipe recipe) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM recipe_ingredient WHERE recipe_id = ?", Integer.class, recipe.id());
    }
}
