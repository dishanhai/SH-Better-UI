// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26;

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideMe26ArtifactContractTest {
    private static final String BUILDER = "guideme/layout/flow/LineBuilder";
    private static final String RUN = "guideme/layout/flow/LineTextRun";
    private static final String CONTEXT = "guideme/render/RenderContext";
    private static final String CONTENT = "Lguideme/document/flow/LytFlowContent;";
    private static final String STYLE = "Lguideme/style/ResolvedTextStyle;";
    private static final String STRING = "Ljava/lang/String;";

    @Test
    void official26_2ArtifactStillHasBothRequiredInjectionPoints() throws Exception {
        var artifact = Path.of(System.getProperty("shbui.guideme26.artifact"));
        try (var jar = new JarFile(artifact.toFile())) {
            method(jar, BUILDER, "appendText", "(" + STRING + CONTENT + ")V");
            var emit = method(jar, BUILDER, "lambda$appendText$0",
                    "(" + STYLE + STYLE + CONTENT + "Ljava/lang/CharSequence;FZ)V");
            assertSingleCall(emit, RUN, "<init>", "(" + STRING + STYLE + STYLE + ")V");
            var render = method(jar, RUN, "render", "(L" + CONTEXT + ";)V");
            assertSingleCall(render, CONTEXT, "renderText", "(" + STRING + STYLE + "FF)V");
            method(jar, CONTEXT, "guiGraphics", "()Lnet/minecraft/client/gui/GuiGraphicsExtractor;");
            method(jar, CONTEXT, "font", "()Lnet/minecraft/client/gui/Font;");
        }
    }

    private static MethodModel method(JarFile jar, String type, String name, String descriptor) throws Exception {
        try (var input = jar.getInputStream(jar.getJarEntry(type + ".class"))) {
            return ClassFile.of().parse(input.readAllBytes()).methods().stream()
                    .filter(method -> method.methodName().equalsString(name)
                            && method.methodType().equalsString(descriptor))
                    .findFirst().orElseThrow(() -> new AssertionError(type + "." + name + descriptor));
        }
    }

    private static void assertSingleCall(MethodModel method, String type, String name, String descriptor) {
        long count = method.code().orElseThrow().elementStream()
                .filter(step -> step instanceof InvokeInstruction call
                        && call.owner().asInternalName().equals(type)
                        && call.name().equalsString(name)
                        && call.type().equalsString(descriptor)).count();
        assertEquals(1, count, type + "." + name + descriptor);
    }
}
