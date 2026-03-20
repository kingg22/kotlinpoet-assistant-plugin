import com.squareup.kotlinpoet.CodeBlock

fun test() {
    CodeBlock.builder().add("«%L %name:S»", "a", "b")
}
