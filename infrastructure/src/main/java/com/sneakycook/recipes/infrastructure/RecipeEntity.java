package com.sneakycook.recipes.infrastructure;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping of the recipe aggregate. Deliberately separate from the domain
 * {@code Recipe} record so the domain stays framework-free; the
 * {@link RecipeEntityMapper} bridges the two. The generated
 * {@code instructions_tsv} column is read-only here so full-text predicates
 * can reference it from JPA Specifications [REQ-9].
 */
@Entity
@Table(name = "recipe")
public class RecipeEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean vegetarian;

    @Column(nullable = false)
    private int servings;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_ingredient", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 100)
    private List<String> ingredients;

    @Column(nullable = false)
    private String instructions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Generated column; mapped read-only for full-text Criteria predicates. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "instructions_tsv", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.OTHER)
    private Object instructionsTsv;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVegetarian() {
        return vegetarian;
    }

    public void setVegetarian(boolean vegetarian) {
        this.vegetarian = vegetarian;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Object getInstructionsTsv() {
        return instructionsTsv;
    }
}
