package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;
import com.sneakycook.recipes.domain.RecipeSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing the domain port [REQ-12]. Transaction
 * demarcation lives here because the application module is Spring-free: one
 * use-case execution equals one transaction (spec §3).
 */
@Repository
@Transactional
class RecipeRepositoryAdapter implements RecipeRepository {

    private final RecipeJpaRepository jpa;
    private final RecipeEntityMapper mapper;

    RecipeRepositoryAdapter(RecipeJpaRepository jpa, RecipeEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Recipe save(Recipe recipe) {
        return mapper.toDomain(jpa.save(mapper.toEntity(recipe)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipe> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipePage search(RecipeFilter filter) {
        Page<RecipeEntity> page = jpa.findAll(
                RecipeSpecifications.matches(filter),
                PageRequest.of(filter.page(), filter.size(), toSpringSort(filter.sort())));
        return new RecipePage(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * The id tie-breaker makes ordering total, so pages stay disjoint even
     * when the sort key ties (e.g. equal createdAt within one microsecond).
     */
    private static Sort toSpringSort(RecipeSort sort) {
        Sort.Direction direction = switch (sort.direction()) {
            case ASC -> Sort.Direction.ASC;
            case DESC -> Sort.Direction.DESC;
        };
        String property = switch (sort.field()) {
            case NAME -> "name";
            case SERVINGS -> "servings";
            case CREATED_AT -> "createdAt";
        };
        return Sort.by(direction, property).and(Sort.by("id"));
    }
}
