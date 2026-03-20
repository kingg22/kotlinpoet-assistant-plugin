import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().addNamed("%1L %2S", mapOf("a" to "x"))
}
