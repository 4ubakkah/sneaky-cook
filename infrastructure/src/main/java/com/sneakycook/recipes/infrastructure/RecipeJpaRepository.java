package com.sneakycook.recipes.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** Spring Data gateway; only the adapter sees it. */
interface RecipeJpaRepository extends JpaRepository<RecipeEntity, UUID>, JpaSpecificationExecutor<RecipeEntity> {

    /** Owner-scoped lookup [REQ-18]: a foreign id is indistinguishable from a missing one. */
    Optional<RecipeEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
