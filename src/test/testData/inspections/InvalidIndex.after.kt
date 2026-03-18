import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%1L value", "arg")
}
