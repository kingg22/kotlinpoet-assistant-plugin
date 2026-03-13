import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("val %N = %S", "myVar", "myArg")
}
