import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%L %2S", "a", "b")
}
