import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().addNamed("%name:S", mapOf("name" to "value"))
}
