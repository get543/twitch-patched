package dev.twitchpatches.patches.twitch.ui

import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import dev.twitchpatches.patches.twitch.reload.nativeReloadMethod
import org.junit.Assert.*
import org.junit.Test

class UiCallSitesTest {
    private val first = ImmutableMethodReference("Lfixture/Controls;", "render", listOf("I"), "V")
    private val second = ImmutableMethodReference("Lfixture/Actions;", "render", listOf("I"), "V")

    @Test fun groupsMultipleTargetsAndInvocationFormatsWithoutDuplicateCallers() {
        val caller = method("caller", """
            const/4 v0, 0x0
            invoke-static {v0}, $first
            invoke-static/range {v0 .. v0}, $second
            invoke-static/range {v0 .. v0}, $first
            return-void
        """)
        val overload = method("overload", """
            const/4 v0, 0x0
            invoke-static {v0}, Lfixture/Controls;->render(Z)V
            invoke-static {v0}, Lfixture/Other;->render(I)V
            return-void
        """)
        val matches = collectUiActionCallers(listOf(caller, overload), listOf(first, second))
        assertEquals(listOf(caller), matches.getValue(first.toString()))
        assertEquals(listOf(caller), matches.getValue(second.toString()))
    }

    @Test fun traversesEachMethodOnceAndReturnsEmptyListsForAbsentTargets() {
        var reads = 0
        val source = method("unrelated", "return-void")
        val counted = object : Method by source {
            override fun getImplementation(): MethodImplementation? {
                reads++
                return source.implementation
            }
        }
        val matches = collectUiActionCallers(listOf(counted), listOf(first, second))
        assertEquals(1, reads)
        assertTrue(matches.values.all { it.isEmpty() })
    }

    private fun method(name: String, code: String): Method = ImmutableMethod.of(nativeReloadMethod(
        "Lfixture/Caller;", name, emptyList(), "V", 1, code, static = true))
}
