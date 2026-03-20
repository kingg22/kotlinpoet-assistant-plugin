import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%L %name:L %L", 1, 2, 3)
}
