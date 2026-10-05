package com.ktayl.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The module-boundary guard (L1 — pure classpath/bytecode analysis via ArchUnit; NO Spring context,
 * NO database). {@link ApplicationModules#verify()} fails the build on any boundary violation:
 * a module reaching into another module's internals, a cross-module cyclic dependency, or a
 * dependency a module hasn't declared.
 *
 * <p>This is how ktayl-core enforces the modular-monolith discipline by TOOLING rather than by review.
 *
 * <p>To prove the guard bites: temporarily make a type in {@code billing} reference a type in another
 * CLOSED module's internal (non-API) package — {@code verify()} then fails here. Do not commit that.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(KtaylCoreApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
