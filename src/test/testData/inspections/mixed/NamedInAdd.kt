import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%name:L %other:S", "a", "b")
}
