# Jarvis Feature Coverage

This map includes only behavior with an existing named JVM test. The CI validator checks that each referenced test method remains present in its declared test file.

| Feature | Implementation | Named test method |
|---|---|---|
| Floating overlay visual state mapping | `mapFloatingViewStyle` | `app/src/test/java/com/openjarvis/ui/overlay/FloatingViewStateTest.kt::collapsedStateMapsVoiceStatesToDistinctColorsAndLabels`; `app/src/test/java/com/openjarvis/ui/overlay/FloatingViewStateTest.kt::expandedStateKeepsVoiceColorAndUsesPanelLabel` |

## DEVICE-ONLY

- Floating bubble drag, edge snapping, touch pass-through, outside-tap collapse, and Back-key behavior require verification on a physical Android device. JVM unit tests cannot prove WindowManager touch routing or OEM overlay behavior.
