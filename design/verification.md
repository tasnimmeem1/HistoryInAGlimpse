# Runtime verification

All delivered Java sources compiled with JDK17 and JavaFX21.0.6.
Headless JavaFX application checks passed:
- Four separate person sprites plus one fire sprite.
- Five distinct cycle durations (6.7, 5.3, 4.1, 8.9 and 0.87 seconds).
- Animation is the first tab and automatically starts all five actors.
- Pausing just the father freezes his frame while the child's pose still changes.
- Selecting Era map pauses all five; returning resumes them.
- Eighty frames captured from the actual JavaFX scene for the eight-second preview.
The preview deliberately pauses the father briefly to demonstrate independence.
Windows narration and audible hardware output still need a local check.

Rename verification: window title, splash creator credit, Explore history entry,
and independent animation/map navigation passed in JavaFX after the rename.
