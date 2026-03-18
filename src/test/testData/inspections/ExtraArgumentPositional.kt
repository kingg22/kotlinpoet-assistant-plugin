import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("Hello %2L", "unusedFirst", "world")
}
