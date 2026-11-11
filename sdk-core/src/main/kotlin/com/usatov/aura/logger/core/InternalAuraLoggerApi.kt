package com.usatov.aura.logger.core

/**
 * Marks declarations that are public only so that the Aura Logger modules can use each other
 * (for example, the `sdk` module implementing ports declared in `sdk-core`).
 *
 * They are not part of the supported client API: they can change or disappear in any release
 * without notice. Clients must not opt in.
 */
@RequiresOptIn(
    message = "Internal Aura Logger API. It is not meant for SDK clients and can change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalAuraLoggerApi
