package com.sneakycook.recipes.domain;

import java.util.List;

/**
 * One page of search results with the envelope numbers the API exposes
 * [REQ-4]. Totals always reflect the <em>filtered</em> result set, not the
 * whole table.
 */
public record RecipePage(
        List<Recipe> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public RecipePage {
        content = List.copyOf(content);
    }
}
