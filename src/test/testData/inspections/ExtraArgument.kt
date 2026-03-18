import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("Hello %L", "world", "extraValue")
}
