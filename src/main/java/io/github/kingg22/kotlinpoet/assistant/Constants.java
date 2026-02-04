package io.github.kingg22.kotlinpoet.assistant;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;

public final class Constants {
    private Constants() {
        throw new UnsupportedOperationException();
    }

    private static final FqName KOTLINPOET_PACKAGE = new FqName("com.squareup.kotlinpoet");

    public static final class ClassIds {
        private ClassIds() {
            throw new UnsupportedOperationException();
        }

        public static final ClassId CODE_BLOCK = new ClassId(KOTLINPOET_PACKAGE, Name.identifier("CodeBlock"));

        public static final ClassId CODE_BLOCK_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, Name.identifier("CodeBlock.Builder"));

        public static final ClassId FUNSPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, Name.identifier("FunSpec.Builder"));

        public static final ClassId TYPE_SPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, Name.identifier("TypeSpec.Builder"));
        public static final ClassId PROPERTYSPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, Name.identifier("PropertySpec.Builder"));
        public static final ClassId FILESPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, Name.identifier("FileSpec.Builder"));

        public static @NotNull ClassId[] ALL = {
            CODE_BLOCK, CODE_BLOCK_BUILDER, FUNSPEC_BUILDER, TYPE_SPEC_BUILDER, PROPERTYSPEC_BUILDER, FILESPEC_BUILDER
        };
    }
}
