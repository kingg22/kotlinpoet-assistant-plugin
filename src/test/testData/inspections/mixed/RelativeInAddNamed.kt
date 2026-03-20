import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().addNamed("%L %S", mapOf("a" to "x"))
}
