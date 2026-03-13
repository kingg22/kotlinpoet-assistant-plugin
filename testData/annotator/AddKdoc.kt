import com.squareup.kotlinpoet.PropertySpec

fun test() {
    PropertySpec.builder("answer", Int::class)
        .addKdoc("Value is %L", 42)
}
