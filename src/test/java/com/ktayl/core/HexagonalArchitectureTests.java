package com.ktayl.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces hexagonal purity (ADR-013) by tooling, not review — the companion to {@link ModularityTests}
 * (which guards the module boundaries). The build FAILS if a {@code ..domain..} type imports Spring, JPA or
 * any infrastructure type, so the business logic stays independent of the tech ("survives it"). Runs on the
 * classpath only — no Spring context, no database.
 */
class HexagonalArchitectureTests {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ktayl.core");

    @Test
    void domain_packages_import_no_framework() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..")
                .as("a module's domain/ must be pure Java — no Spring, no JPA (ADR-013)")
                .check(classes);
    }

    @Test
    void domain_does_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .as("dependencies point inward only — domain never depends on infrastructure (ADR-013)")
                .check(classes);
    }
}
