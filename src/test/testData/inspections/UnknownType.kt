import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%Z value", "arg")
}
