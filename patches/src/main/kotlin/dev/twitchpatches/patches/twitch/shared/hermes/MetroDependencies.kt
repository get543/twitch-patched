package dev.twitchpatches.patches.twitch.shared.hermes

internal fun HermesBundle.dependencyModule(functionName: String, property: String): Int {
    val function = functions.filter { strings[it.name] == functionName && it.params == 2 }
        .singleOrNull() ?: error("Modern Twitch: expected one $functionName consumer.")
    val code = instructions(function)
    val read = code.withIndex().filter { it.value.name.startsWith("GetById") && strings[it.value.args.last()] == property }
        .singleOrNull() ?: error("Modern Twitch: $functionName dependency read changed.")
    val slot = code.take(read.index).lastOrNull { it.name == "LoadFromEnvironment" && it.args[0] == read.value.args[1] }
        ?.args?.get(2) ?: error("Modern Twitch: $property dependency environment changed.")
    val factory = functions.filter { it.params == 8 }.singleOrNull { parent ->
        instructions(parent).any { it.name == "CreateClosure" && it.args[2] == function.id }
    } ?: error("Modern Twitch: $functionName factory is ambiguous.")
    val body = instructions(factory)
    val environment = body.single { it.name == "CreateFunctionEnvironment" }.args[0]
    val store = body.withIndex().singleOrNull { it.value.name == "StoreToEnvironment" &&
        it.value.args[0] == environment && it.value.args[1] == slot }
        ?: error("Modern Twitch: $property dependency slot changed.")
    val call = body.getOrNull(store.index - 1)
    val dependency = body.getOrNull(store.index - 2)
    val requireRegister = body.single { it.name == "LoadParam" && it.args[1] == 2 }.args[0]
    val dependenciesRegister = body.single { it.name == "LoadParam" && it.args[1] == 7 }.args[0]
    require(call?.name == "Call2" && dependency?.name == "GetByIndex" &&
        call.args[0] == store.value.args[2] && call.args[1] == requireRegister &&
        call.args[3] == dependency.args[0] && dependency.args[1] == dependenciesRegister) {
        "Modern Twitch: $property import flow changed."
    }
    val globalCode = instructions(functions[global])
    val definition = globalCode.withIndex().single { it.value.name == "CreateClosure" && it.value.args[2] == factory.id }
    val define = globalCode.getOrNull(definition.index + 1)
    require(define?.name == "Call4" && define.args[3] == definition.value.args[0]) {
        "Modern Twitch: $functionName module definition changed."
    }
    val array = globalCode.take(definition.index).lastOrNull {
        it.name.startsWith("NewArrayWithBuffer") && it.args[0] == define.args[5]
    } ?: error("Modern Twitch: $functionName dependency array missing.")
    return integerArray(array.args[3], array.args[2])[dependency.args[2]]
}
