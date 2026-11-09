package com.focusstudy.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class BuildConfigHygieneTest {

    /** A machine-specific JDK path in the committed gradle.properties breaks every other machine and CI. */
    @Test
    fun gradleProperties_hasNoHardcodedJavaHome() {
        val props = File("../gradle.properties").readText()
        assertFalse(props.lines().any { it.trim().startsWith("org.gradle.java.home") })
    }
}
