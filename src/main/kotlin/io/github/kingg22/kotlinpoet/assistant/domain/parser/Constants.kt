package io.github.kingg22.kotlinpoet.assistant.domain.parser

import java.util.regex.Pattern

val NAMED_ARGUMENT_PATTERN: Pattern = "%([\\w_]+):(\\w)".toPattern()
val NAMED_ARGUMENT_REGEX: Regex = NAMED_ARGUMENT_PATTERN.toRegex()
