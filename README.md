# SimpleSets
This repository contains a prototype implementation of SimpleSets.
Given a set of points in the plane that each belong to one category, SimpleSets creates simple shapes that enclose patterns in the data.
The implementation is written in the Kotlin programming language.
The computational part of the code is multiplatform.
Two UIs are available: a web UI and a JVM UI.
The web UI is more accessible and easier to use, but the JVM UI performs the computations faster.

[![WebUI-scrot](https://github.com/Yvee1/SimpleSets/assets/30909373/9f3c43c5-150c-462c-857d-58393a002da4)](https://pages.stevenvdb.com/SimpleSets/)

## Setting up the project locally
The easiest way to use the implementation is to use [the web UI](https://pages.stevenvdb.com/SimpleSets/).
If you want to use the JVM-based implementation or modify the code, you need to set up the project locally.
One way to do this is as follows.
1. [Download IntelliJ IDEA](https://www.jetbrains.com/idea/download/)
2. Download the SimpleSets repository and open it as a project in IntelliJ IDEA (this may take a while to load).
3. In the IDE move to `src/jvmMain/Main.kt` and run this file by clicking on the green arrow next to the main function. This should cause a window to appear. If there is a NoClassDefFoundError about `mu/KotlinLogging` then do the following. At the top of the screen next to the screen arrow click on the three dots and 'Edit (configuration)'. In the screen that opens click the plus sign at the top left and select 'Gradle'. Under Run fill in "jvmRun -DmainClass=MainKt --quiet", and leave the rest as the default. Click save and run this configuration.
4. The left blue part of the screen is the settings window. Input points have to be loaded from a file in the `input-output` directory. Pressing F11 toggles the visibility of the settings window.

## Remarks
This implementation does not deal with the case where a stacking preference cannot be satisfied. 
However, this does not occur in any dataset we have encountered.
Euler spirals for smooth curves are approximated using Hobby's algorithm.
The implementation is sensitive to the distribution of points.
Areas that are too dense (either by using a large point size or having close point positions) may cause the implementation to crash.
In particular, if in the web page the loading bar of Computing... is stuck at the end, then the implementation has crashed behind the scenes.
In such a case, please reload the web page and try again with a smaller point size setting.
