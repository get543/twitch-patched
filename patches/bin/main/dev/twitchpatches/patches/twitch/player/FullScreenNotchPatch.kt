package dev.twitchpatches.patches.twitch.player

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import dev.twitchpatches.patches.twitch.settings.settingsPatch
import dev.twitchpatches.patches.twitch.shared.*

@Suppress("unused")
val fullScreenNotchPatch = bytecodePatch(
    name = "Landscape edge-to-edge playback",
    description = "Uses immersive edge-to-edge display only while a live player is shown in landscape.",
    default = true,
) {
    compatibleWith(TwitchTarget.candidateCompatibility)
    dependsOn(settingsPatch)
    execute {
        val application = classDefBy("Ltv/twitch/android/app/consumer/TwitchApplication;").methods.filter {
            it.name == "onCreate" && it.isInstance(emptyList(), "V")
        }.uniqueHook("fullscreen notch application initialization")
        val method = mutableClassDefBy(application.definingClass).methods.single { it.reference == application.reference }
        method.code().withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.asReversed().forEach {
            method.insertAtReturn(it,
                "invoke-static/range {p0 .. p0}, Ldev/twitchpatches/extension/player/FullScreenNotchRuntime;->initialize(Landroid/app/Application;)V")
        }

        val portalFragment = classDefBy("Ltv/twitch/android/feature/theatre/portal/PortalTheatreFragment;")
            .methods.filter {
                it.name == "N" &&
                    it.isInstance(
                        listOf("Ltv/twitch/android/shared/portal/fork/TwitchPortalView;"),
                        "V",
                    )
            }.uniqueHook("live theatre player view attachment")
        val attach = mutableClassDefBy(portalFragment.definingClass).methods
            .single { it.reference == portalFragment.reference }
        attach.code().withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
            .asReversed()
            .forEach {
                attach.insertAtReturn(
                    it,
                    "invoke-static/range {p1 .. p1}, Ldev/twitchpatches/extension/player/FullScreenNotchRuntime;->onPlayerViewAttached(Landroid/view/View;)V",
                )
            }
    }
}
