import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().addNamed("%Food:L", mapOf("Food" to "tacos"))
}
