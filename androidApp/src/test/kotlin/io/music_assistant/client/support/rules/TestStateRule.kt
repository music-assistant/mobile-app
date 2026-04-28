package io.music_assistant.client.support.rules

import io.music_assistant.client.ui.Timings
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TestStateRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                Timings.DEBOUNCE = 0
                base.evaluate()
            }
        }
    }
}
