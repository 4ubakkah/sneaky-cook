package com.sneakycook.recipes.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Boot configuration anchoring the {@code @DataJpaTest} slice — the
 * infrastructure module has no production application class by design.
 */
@SpringBootApplication
class InfrastructureTestApplication {
}
