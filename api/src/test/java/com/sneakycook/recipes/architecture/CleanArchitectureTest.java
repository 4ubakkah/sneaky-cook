package com.sneakycook.recipes.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable rendering of the module boundaries (spec §3, REQ-13). Maven
 * enforces the dependency direction between jars; these rules enforce it
 * between packages, so the boundaries survive even if the modules were ever
 * collapsed into one.
 */
class CleanArchitectureTest {

    private static final String DOMAIN = "com.sneakycook.recipes.domain..";
    private static final String APPLICATION = "com.sneakycook.recipes.application..";
    private static final String INFRASTRUCTURE = "com.sneakycook.recipes.infrastructure..";
    private static final String API = "com.sneakycook.recipes.api..";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sneakycook.recipes");
    }

    @Test
    @DisplayName("[REQ-13] the domain is framework-free: depends on nothing but the JDK")
    void domainDependsOnNothingButTheJdk() {
        classes()
                .that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(DOMAIN, "java..")
                .check(productionClasses);
    }

    @Test
    @DisplayName("[REQ-13] use cases are Spring-free: depend only on the domain and the JDK")
    void applicationDependsOnlyOnDomain() {
        classes()
                .that().resideInAPackage(APPLICATION)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(APPLICATION, DOMAIN, "java..")
                .check(productionClasses);
    }

    @Test
    @DisplayName("[REQ-13] nothing reaches into the persistence adapter")
    void infrastructureIsInvisibleToTheRest() {
        noClasses()
                .that().resideOutsideOfPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
                .check(productionClasses);
    }

    @Test
    @DisplayName("[REQ-13] the HTTP edge is a consumer of the core, never a dependency of it")
    void apiIsInvisibleToTheCore() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAPackage(API)
                .check(productionClasses);
    }
}
