package com.ktayl.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ktayl-core — the ktayl-solution insurance-LOB modular monolith.
 *
 * <p>Each direct sub-package of this package is a Spring Modulith application module
 * ({@code billing}, and the cross-cutting open module {@code shared}). Module boundaries are
 * enforced by {@code ModularityTests} ({@code ApplicationModules.verify()}) — a module may call
 * another only via its public API or an application event, never its internals or schema.
 */
@SpringBootApplication
public class KtaylCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(KtaylCoreApplication.class, args);
    }
}
