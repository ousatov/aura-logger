package com.usatov.aura.logger.demo.core.time

import java.time.ZoneId

/** Current device time zone; read on every use because it can change at runtime. Injectable for tests. */
fun interface ZoneProvider {
    fun zone(): ZoneId
}
