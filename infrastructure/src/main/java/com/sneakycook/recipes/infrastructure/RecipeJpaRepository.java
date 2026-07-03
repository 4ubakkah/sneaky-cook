package com.sneakycook.recipes.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data gateway; only the adapter sees it. */
interface RecipeJpaRepository extends JpaRepository<RecipeEntity, UUID> {
}
