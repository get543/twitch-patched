package dev.twitchpatches.patches.twitch.chat

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.twitchpatches.patches.twitch.settings.settingsPatch
import dev.twitchpatches.patches.twitch.shared.*

@Suppress("unused")
val unifiedChatAnimationPatch = bytecodePatch(
    name = "Clip-style chat animation",
    description = "Experimental clip-style chat mode hook; fails when no matching Twitch method is found.",
    default = false,
) {
    compatibleWith(TwitchTarget.candidateCompatibility)
    dependsOn(settingsPatch)
    execute {
        val application = classDefBy("Ltv/twitch/android/app/consumer/TwitchApplication;").methods.filter {
            it.name == "onCreate" && it.isInstance(emptyList(), "V") &&
                it.code().any { instruction -> instruction.opcode == Opcode.RETURN_VOID }
        }.uniqueHook("unified chat animation application initialization")
        val method = mutableClassDefBy(application.definingClass).methods.single { it.reference == application.reference }
        method.code().withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.asReversed().forEach {
            method.insertAtReturn(it,
                "invoke-static/range {p0 .. p0}, Ldev/twitchpatches/extension/chat/UnifiedChatRuntime;->initialize(Landroid/app/Application;)V")
        }

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
        if (candidates.isEmpty()) {
            throw PatchException("Clip-style chat animation: no matching chat-mode hook found; this Twitch APK is not verified.")
        }

        candidates.forEach { (classType, methodReference) ->
            val method = mutableClassDefBy(classType).methods.single { it.reference == methodReference }
            val returns = method.code().withIndex()
                .filter { it.value.opcode == Opcode.RETURN }
                .map { indexed ->
                    val register = (indexed.value as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("Clip-style chat animation: boolean return register not found in $methodReference.")
                    indexed.index to register
                }
            if (returns.isEmpty()) {
                throw PatchException("Clip-style chat animation: matching method $methodReference has no boolean return.")
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
