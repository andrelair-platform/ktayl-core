/**
 * Cross-cutting infrastructure shared by all business modules (config, security, integration
 * clients). Declared an <b>OPEN</b> Spring Modulith module so any business module may depend on it
 * without a boundary violation. It holds NO business logic.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.ktayl.core.shared;
