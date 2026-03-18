import com.squareup.kotlinpoet.CodeBlock

fun test() {
    val flag = true
    CodeBlock.builder().add("%S", flag)
}
