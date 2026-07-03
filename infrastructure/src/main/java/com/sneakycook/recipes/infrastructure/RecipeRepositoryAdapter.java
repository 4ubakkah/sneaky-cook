package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeRepository;
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
}
