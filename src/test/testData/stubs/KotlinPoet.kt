package com.squareup.kotlinpoet

class CodeBlock {
    class Builder {
        fun add(format: String, vararg args: Any?): Builder = this
        fun addNamed(format: String, args: Map<String, Any?>): Builder = this
        fun addStatement(format: String, vararg args: Any?): Builder = this
        fun beginControlFlow(format: String, vararg args: Any?): Builder = this
    }

    companion object {
        fun builder(): Builder = Builder()
        fun of(format: String, vararg args: Any?): CodeBlock = CodeBlock()
    }
}

class FunSpec {
    class Builder
}

class TypeSpec {
    class Builder
}

class PropertySpec {
    class Builder
}

class FileSpec {
    class Builder
}
