package io.music_assistant.client.support.rules

import org.junit.rules.RuleChain

fun createTestRuleChain(): RuleChain {
    return RuleChain
        .outerRule(createKoinTestRule())
        .around(TestStateRule())
}
