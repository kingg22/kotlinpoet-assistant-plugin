import com.squareup.kotlinpoet.CodeBlock

fun test(name: String) {
    CodeBlock.builder().add("Hello " + "%<caret>L", name)
}
