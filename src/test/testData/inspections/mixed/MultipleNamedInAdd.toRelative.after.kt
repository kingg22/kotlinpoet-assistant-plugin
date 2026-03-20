import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%T %N %L", String::class, "myVar", 42)
}
