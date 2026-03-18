import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%0L value", "arg")
}
