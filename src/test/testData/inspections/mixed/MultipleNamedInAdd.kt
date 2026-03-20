import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("%a:T %b:N %c:L", String::class, "myVar", 42)
}
