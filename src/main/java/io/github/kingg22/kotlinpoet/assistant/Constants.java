package io.github.kingg22.kotlinpoet.assistant;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;

import java.util.Set;

public final class Constants {
    private Constants() {
        throw new UnsupportedOperationException();
    }

    private static final FqName KOTLINPOET_PACKAGE = new FqName("com.squareup.kotlinpoet");

    public static final @NotNull Set</*@NotNull */ String> KOTLINPOET_CALLS =
            Set.of("addStatement", "addCode", "CodeBlock.of", "beginControlFlow", "addNamed", "add");

    public static final class ClassIds {
        private ClassIds() {
            throw new UnsupportedOperationException();
        }

        public static final ClassId CODE_BLOCK = new ClassId(KOTLINPOET_PACKAGE, Name.identifier("CodeBlock"));

        public static final ClassId CODE_BLOCK_COMPANION =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("CodeBlock.Companion"), false);

        public static final ClassId CODE_BLOCK_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("CodeBlock.Builder"), false);

        public static final ClassId FUNSPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("FunSpec.Builder"), false);

        public static final ClassId TYPE_SPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("TypeSpec.Builder"), false);
        public static final ClassId PROPERTYSPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("PropertySpec.Builder"), false);
        public static final ClassId FILESPEC_BUILDER =
                new ClassId(KOTLINPOET_PACKAGE, new FqName("FileSpec.Builder"), false);

        public static @NotNull ClassId /*@NotNull */[] ALL = {
            CODE_BLOCK,
            CODE_BLOCK_BUILDER,
            CODE_BLOCK_COMPANION,
            FUNSPEC_BUILDER,
            TYPE_SPEC_BUILDER,
            PROPERTYSPEC_BUILDER,
            FILESPEC_BUILDER
        };
    }
}
