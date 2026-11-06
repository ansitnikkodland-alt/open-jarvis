package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeStepIntentParserTest {
    @Test fun parsesValidAlarmAndTimer() {
        assertEquals(StepIntent.Alarm(7, 30), NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":7,"minute":30}}"""))
        assertEquals(StepIntent.Timer(90), NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":90}}"""))
    }

    @Test fun parsesBooleanAndTextIntents() {
        assertEquals(StepIntent.Torch(true), NativeStepIntentParser.parse("""{"skill":"torch","args":{"on":true}}"""))
        assertEquals(StepIntent.Volume(false), NativeStepIntentParser.parse("""{"skill":"volume","args":{"up":false}}"""))
        assertEquals(StepIntent.Navigate("Pune"), NativeStepIntentParser.parse("""{"skill":"navigate","args":{"place":"Pune"}}"""))
        assertEquals(StepIntent.AppLaunch("Chrome"), NativeStepIntentParser.parse("""{"skill":"app_launch","args":{"label":"Chrome"}}"""))
    }

    @Test fun rejectsInvalidJsonAndOutOfRangeArguments() {
        assertNull(NativeStepIntentParser.parse("not json"))
        assertNull(NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":24,"minute":0}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":0}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"navigate","args":{"place":""}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"torch","args":{"on":"yes"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":"90"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":90.5}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":7.5,"minute":30}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{"contact":42,"text":"hello"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"app_launch","args":{"label":"Chrome","extra":"x"}}"""))
    }

    @Test fun noneAndUnknownSkillsReturnNull() {
        assertNull(NativeStepIntentParser.parse("""{"skill":"none","args":{}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"unknown","args":{}}"""))
    }
}
