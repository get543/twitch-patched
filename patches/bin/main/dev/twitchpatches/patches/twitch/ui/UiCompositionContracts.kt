package dev.twitchpatches.patches.twitch.ui

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import dev.twitchpatches.patches.twitch.shared.*

internal data class UiCompositionContract(
    val composer: String, val concrete: String, val start: MethodReference, val end: MethodReference,
    val collect: MethodReference, val value: MethodReference, val factory: MethodReference, val setter: Method,
    val classic: Method, val vertical: Method, val controls: Map<String, Method>, val support: Method,
    val subscribe: FieldReference, val gift: FieldReference, val bits: Method, val supportCallers: List<Method>,
    val classicActions: Map<String, Method>,
    val classicRow: UiClassicActionContract,
    val actionCallers: Map<String, List<Method>> = emptyMap(),
)

internal fun BytecodePatchContext.resolveUiComposition(all: List<Method>): UiCompositionContract {
    val volume = all.filter { it.returnType == "V" && it.parameterTypes.size == 4 &&
        it.parameterTypes.first() == "Z" && it.hasResource("string/mute_button_text") }
        .uniqueHook("UI native volume renderer")
    val composer = volume.parameterTypes[2].toString()
    val callback = volume.parameterTypes[1].toString()
    val callers = all.filter { it.definingClass == volume.definingClass && it.parameterTypes.count { p -> p == callback } >= 8 &&
        it.references().any { ref -> ref.toString() == volume.reference ||
            (ref is MethodReference && ref.definingClass == "Ldev/twitchpatches/extension/reload/NativeReloadBridge;" &&
                ref.name == "renderVolumeAndReload") } }
    require(callers.size == 2) { "UI settings: native player layouts changed." }
    val classic = callers.minBy { it.parameterTypes.size }
    val vertical = callers.maxBy { it.parameterTypes.size }
    val concrete = volume.code().filter { it.opcode == Opcode.CHECK_CAST }.mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type
    }.uniqueHook("UI native concrete composer")
    val start = classic.references().filterIsInstance<MethodReference>().filter {
        it.definingClass == concrete && it.parameterTypes == listOf("I") && it.returnType == "V"
    }.distinctBy { it.toString() }.uniqueHook("UI replaceable composition group")
    val startIndex = classic.code().indexOfFirst { (it as? ReferenceInstruction)?.reference?.toString() == start.toString() }
    val end = classic.code().drop(startIndex + 1).mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
        .first { it.definingClass == concrete && it.parameterTypes.isEmpty() && it.returnType == "V" }
    val fragment = classDefBy("Ltv/twitch/android/feature/theatre/vertical/VerticalTheatreFragment;")
    val controlTypes = fragment.methods.filter { it.parameterTypes.isEmpty() }.map { it.returnType }.toSet()
    val controls = all.filter { it.definingClass in controlTypes &&
        it.hasStrings("mute_button", "go_to_classic_view", "player_settings_button") }
        .uniqueHook("UI native controls owner")
    val root = all.filter { it.definingClass == volume.definingClass && it.references().any { ref ->
        ref is TypeReference && ref.type == controls.definingClass
    } &&
        it.hasStrings("No ViewModelStoreOwner was provided via LocalViewModelStoreOwner") }.uniqueHook("UI controls composition root")
    val collect = root.references().filterIsInstance<MethodReference>().filter {
        it.parameterTypes.size == 2 && it.parameterTypes[1] == composer && it.parameterTypes[0].startsWith("L") &&
            classDefBy(it.parameterTypes[0].toString()).methods.any { method ->
                method.name == "getValue" &&
                    method.parameterTypes.isEmpty() && method.returnType == "Ljava/lang/Object;"
            }
    }.distinctBy { it.toString() }.uniqueHook("UI lifecycle state collector")
    val value = root.references().filterIsInstance<MethodReference>().filter {
        it.name == "getValue" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;"
    }.distinctBy { it.toString() }.uniqueHook("UI collected preference value")
    val constructor = all.filter { it.name == "<init>" && it.hasStrings("VerticalTheatrePlayable", "VerticalTheatre", "video_cc") }
        .uniqueHook("UI player preference flow owner")
    val factory = constructor.references().filterIsInstance<MethodReference>().filter {
        it.parameterTypes == listOf("Ljava/lang/Object;") && it.returnType.startsWith("L") && classDefBy(it.returnType).methods.any { method ->
            method.name == "getValue" && method.parameterTypes.isEmpty()
        }
    }.distinctBy { it.toString() }.uniqueHook("UI mutable preference flow factory")
    val setter = classDefBy(factory.returnType).methods.filter {
        it.isInstance(listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), "Z") && it.code().any { code -> code.opcode == Opcode.MONITOR_ENTER }
    }.uniqueHook("UI mutable preference flow setter")
    val renderers = all.filter { it.definingClass == volume.definingClass && AccessFlags.STATIC.isSet(it.accessFlags) }
    val share = renderers.filter { it.parameterTypes == listOf(callback, composer, "I") && it.hasResource("string/share") }
        .uniqueHook("UI native Share renderer")
    val cast = renderers.filter { it.parameterTypes == share.parameterTypes && it.references().filterIsInstance<MethodReference>()
        .filter { ref -> ref.name == "<init>" }.any { ref -> classDefBy(ref.definingClass).methods.any { method ->
            method.references().filterIsInstance<MethodReference>().any { call ->
                call.definingClass == "Landroidx/mediarouter/app/MediaRouteButton;" && call.name == "<init>"
            }
        } } }.uniqueHook("UI native Cast renderer")
    val clip = renderers.filter { it.parameterTypes == listOf("I", composer, callback, callback, "Z") &&
        vertical.references().any { ref -> ref.toString() == it.reference } }.uniqueHook("UI native Clip renderer")
    val support = all.filter { it.parameterTypes.size == 7 && it.parameterTypes[5] == composer &&
        it.hasResource("string/follow") && it.hasResource("string/subscribe") && it.hasResource("string/gift_a_sub") }
        .uniqueHook("UI native channel action renderer")
    val classicGift = all.filter { it.parameterTypes == listOf("I", composer, callback, support.parameterTypes[0], "Z") &&
        it.hasResource("string/gift_a_sub") }.uniqueHook("UI classic Gift a Sub renderer")
    val classicSubscribe = all.filter { it.definingClass == classicGift.definingClass &&
        it.parameterTypes == classicGift.parameterTypes + "Z" && it.hasResource("string/subscribe") }
        .uniqueHook("UI classic Subscribe renderer")
    val classicRow = resolveClassicActionRow(all, composer)
    val actionCallers = collectUiActionCallers(all.asSequence().filter {
        it.definingClass != "Ldev/twitchpatches/extension/ui/UiComposeBridge;"
    }.asIterable(), listOf(support, classicGift, classicSubscribe, classicRow.renderer))
    val supportCallers = actionCallers.getValue(support.reference)
    val bits = all.filter { it.parameterTypes.size == 7 && it.parameterTypes[4] == composer && it.hasResource("string/bits") &&
        supportCallers.any { caller -> caller.references().any { ref -> ref.toString() == it.reference } } }
        .uniqueHook("UI native Bits renderer")
    fun model(label: String): FieldReference {
        val index = support.code().indexOfFirst { (it as? WideLiteralInstruction)?.wideLiteral == uiResourceIds[label] }
        return support.code().take(index).last { it.opcode == Opcode.SGET_OBJECT }.let {
            (it as ReferenceInstruction).reference as FieldReference
        }.also { require(classDefBy(it.definingClass).superclass == support.parameterTypes[1]) {
            "UI settings: native channel action model changed."
        } }
    }
    return UiCompositionContract(composer, concrete, start, end, collect, value, factory, setter,
        classic, vertical, mapOf("clip" to clip, "cast" to cast, "share" to share), support,
        model("string/subscribe"), model("string/gift_a_sub"), bits, supportCallers,
        mapOf("subscribe" to classicSubscribe, "gift" to classicGift), classicRow, actionCallers)
}

private fun Method.hasResource(name: String) = implementation?.instructions?.any {
    (it as? WideLiteralInstruction)?.wideLiteral == uiResourceIds[name]
} == true
