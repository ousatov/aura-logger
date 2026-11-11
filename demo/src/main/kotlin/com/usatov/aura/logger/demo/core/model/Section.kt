package com.usatov.aura.logger.demo.core.model

/**
 * Top-level sections of the app (bottom navigation tabs).
 *
 * @property key stable value used in analytics events; never localised or renamed.
 */
enum class Section(val key: String) {
    DASHBOARD("dashboard"),
    EVENTS("events"),
    SETTINGS("settings"),
}
