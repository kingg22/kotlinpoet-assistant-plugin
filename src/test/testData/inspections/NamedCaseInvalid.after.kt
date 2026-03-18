import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().addNamed("%food:L", mapOf("food" to "tacos"))
}
