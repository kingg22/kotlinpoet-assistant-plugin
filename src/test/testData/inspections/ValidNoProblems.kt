import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("Hello %L %S", "world", "greeting")
    CodeBlock.builder().addNamed("%food:L %count:L", mapOf("food" to "tacos", "count" to 3))
}
