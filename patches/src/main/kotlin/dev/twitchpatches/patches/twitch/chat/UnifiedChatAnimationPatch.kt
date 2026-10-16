package dev.twitchpatches.patches.twitch.chat

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.twitchpatches.patches.twitch.settings.settingsPatch
import dev.twitchpatches.patches.twitch.shared.*

@Suppress("unused")
val unifiedChatAnimationPatch = bytecodePatch(
    name = "Clip-style chat animation",
    description = "Experimental clip-style chat mode hook.",
    default = false,
) {
    compatibleWith(TwitchTarget.candidateCompatibility)
    dependsOn(settingsPatch)
    execute {
        val runtime = "Ldev/twitchpatches/extension/chat/UnifiedChatRuntime;"
        val candidates = mutableListOf<Pair<String, String>>()
        classDefForEach { type ->
            type.methods.filter {
                it.returnType == "Z" &&
                    (it.name.contains("isUnifiedChat", ignoreCase = true) ||
                        it.name.contains("useClipChat", ignoreCase = true))
            }.forEach { method ->
                candidates += type.type to method.reference
            }
        }

        candidates.forEach { (classType, methodReference) ->
            val method = mutableClassDefBy(classType).methods.single { it.reference == methodReference }
            val returns = method.code().withIndex()
                .filter { it.value.opcode == Opcode.RETURN }
                .mapNotNull { indexed ->
                    val register = (indexed.value as? OneRegisterInstruction)?.registerA
                    if (register != null) indexed.index to register else null
                }
            returns.asReversed().forEach { (index, register) ->
                method.insertBeforeWithLabels(index, """
                    invoke-static/range {v$register .. v$register}, $runtime->resolveMode(Z)Z
                    move-result v$register
                """)
            }
        }
    }
}
