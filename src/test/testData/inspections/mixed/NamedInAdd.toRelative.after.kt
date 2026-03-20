import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%L %S", "a", "b")
}
