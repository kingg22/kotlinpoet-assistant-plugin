package io.github.kingg22.kotlinpoet.assistant;

import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;

public final class Constants {
    private Constants() {
        throw new UnsupportedOperationException();
    }

    private static final FqName KOTLINPOET_PACKAGE =
        new FqName("com.squareup.kotlinpoet");

    public static final class ClassIds {
        private ClassIds() {
            throw new UnsupportedOperationException();
        }

        public static final ClassId CODE_BLOCK =
            new ClassId(KOTLINPOET_PACKAGE, Name.identifier("CodeBlock"));

        public static final ClassId CODE_BLOCK_BUILDER =
            new ClassId(KOTLINPOET_PACKAGE, Name.identifier("CodeBlock.Builder"));
    }
}
