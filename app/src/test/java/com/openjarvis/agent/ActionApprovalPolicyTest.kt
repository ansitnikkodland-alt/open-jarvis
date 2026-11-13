package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionApprovalPolicyTest {
    @Test fun modifyingActionsAlwaysRequireApproval() {
        listOf(
            Action.OPEN_APP,
            Action.TAP,
            Action.TAP_COORDS,
            Action.LONG_PRESS,
            Action.TYPE,
            Action.SWIPE,
            Action.SCROLL,
            Action.CLEAR_TYPE,
            Action.PRESS_BACK,
            Action.PRESS_HOME,
            Action.PRESS_RECENTS
        ).forEach { type ->
            assertTrue(type, ActionApprovalPolicy.requiresApproval(Action(type)))
        }
    }

    @Test fun readOnlyAndErrorActionsDoNotNeedApproval() {
        listOf(Action.WAIT_FOR, Action.READ_SCREEN, Action.EXTRACT_TEXT, Action.ERROR).forEach {
            assertFalse(it, ActionApprovalPolicy.requiresApproval(Action(it)))
        }
    }

    @Test fun unknownActionFailsClosed() {
        assertTrue(ActionApprovalPolicy.requiresApproval(Action("new_unknown_action")))
    }

    @Test fun descriptionDisplaysExactInputAndTarget() {
        val description = ActionApprovalPolicy.describe(
            Action(action = Action.TYPE, packageName = "com.example", value = "exact input")
        )
        assertTrue(description.contains("com.example"))
        assertTrue(description.contains("exact input"))
    }
}
