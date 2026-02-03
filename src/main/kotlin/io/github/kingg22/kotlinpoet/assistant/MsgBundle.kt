package io.github.kingg22.kotlinpoet.assistant

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object MsgBundle : DynamicBundle(
    MsgBundle::class.java,
    MsgBundle.BUNDLE,
) {
    private const val BUNDLE = "messages.MyBundle"

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)

    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> =
        getLazyMessage(key, *params)
}
