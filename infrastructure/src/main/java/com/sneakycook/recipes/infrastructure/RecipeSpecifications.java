package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.RecipeFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Composable filter predicates [REQ-5..REQ-8, REQ-10]: every criterion present
 * in the filter contributes one predicate, all AND-ed into a single query — no
 * special-cased combinations anywhere. Ingredient matching is plain equality
 * against lower-cased stored names; the filter value object has already
 * lower-cased the criteria.
 *
 * <p>Full-text search on instructions [REQ-9] joins in build-order step 6.
 */
final class RecipeSpecifications {

    private RecipeSpecifications() {
    }

    static Specification<RecipeEntity> matches(RecipeFilter filter) {
        List<Specification<RecipeEntity>> parts = new ArrayList<>();
        if (filter.vegetarian() != null) {
            parts.add(vegetarianIs(filter.vegetarian()));
        }
        if (filter.servings() != null) {
            parts.add(servingsAre(filter.servings()));
        }
        // AND semantics [REQ-7]: one EXISTS per required ingredient.
        filter.includeIngredients().forEach(ingredient -> parts.add(containsIngredient(ingredient)));
        if (!filter.excludeIngredients().isEmpty()) {
            parts.add(containsNoneOf(filter.excludeIngredients()));
        }
        return Specification.allOf(parts);
    }

    /** [REQ-5] Exact match on the vegetarian flag. */
    private static Specification<RecipeEntity> vegetarianIs(boolean vegetarian) {
        return (root, query, cb) -> cb.equal(root.get("vegetarian"), vegetarian);
    }

    /** [REQ-6] Exact match on the number of servings. */
    private static Specification<RecipeEntity> servingsAre(int servings) {
        return (root, query, cb) -> cb.equal(root.get("servings"), servings);
    }

    /** [REQ-7] EXISTS: the recipe has this ingredient. */
    private static Specification<RecipeEntity> containsIngredient(String ingredient) {
        return (root, query, cb) -> cb.exists(
                ingredientRows(root.get("id"), query, cb,
                        (subRoot, sub) -> cb.equal(subRoot.join("ingredients"), ingredient)));
    }

    /**
     * [REQ-8] NOT EXISTS: the recipe has none of these ingredients. One
     * correlated subquery for the whole exclusion list — a recipe is rejected
     * as soon as any listed ingredient is present (NONE semantics).
     */
    private static Specification<RecipeEntity> containsNoneOf(List<String> ingredients) {
        return (root, query, cb) -> cb.not(cb.exists(
                ingredientRows(root.get("id"), query, cb,
                        (subRoot, sub) -> subRoot.join("ingredients").in(ingredients))));
    }

    private static Subquery<UUID> ingredientRows(
            jakarta.persistence.criteria.Path<UUID> recipeId,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            IngredientPredicate predicate) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<RecipeEntity> subRoot = sub.from(RecipeEntity.class);
        sub.select(subRoot.get("id")).where(
                cb.equal(subRoot.get("id"), recipeId),
                predicate.toPredicate(subRoot, sub));
        return sub;
    }

    @FunctionalInterface
    private interface IngredientPredicate {
        jakarta.persistence.criteria.Predicate toPredicate(Root<RecipeEntity> subRoot, Subquery<UUID> sub);
    }
}
