// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles the actual strings users can copy against the target loader's classes. */
class GalleryExamplesTest {
    @TempDir Path output;

    @Test
    void publishedExamplesCompileAsScreenMembers() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        String classpath = System.getProperty("shbui.examples.compileClasspath");
        assertNotNull(classpath);
        for (String id : List.of("button", "toggle", "capsule_toggle", "slider", "disclosure", "sections",
                "scrollable_sections", "underlined_tabs", "stepper", "dialog", "toast")) {
            String example = GalleryExamples.code(id);
            assertNotNull(example, id);
            int boundary = example.indexOf("\n\n");
            String source = example.substring(0, boundary) + "\n"
                    + "import net.minecraft.client.gui.screens.Screen;\n"
                    + "import net.minecraft.network.chat.Component;\n"
                    + "final class Example extends Screen {\n"
                    + "    Example() { super(Component.empty()); }\n"
                    + example.substring(boundary + 2) + "\n}";
            Path file = output.resolve("Example.java");
            Files.writeString(file, source, StandardCharsets.UTF_8);
            var diagnostics = new ByteArrayOutputStream();
            int result = compiler.run(null, null, diagnostics, "-proc:none", "-encoding", "UTF-8",
                    "-classpath", classpath, "-d", output.toString(), file.toString());
            assertEquals(0, result, () -> id + ":\n" + diagnostics.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void documentedExtensionExampleCompiles() throws Exception {
        String guide = Files.readString(Path.of("../docs/NEOFORGE_26_GALLERY_GAPS.md"));
        int heading = guide.indexOf("第三方客户端可在初始化时登记");
        int start = guide.indexOf("```java\n", heading);
        int end = guide.indexOf("```", start + 8);
        assertTrue(heading >= 0 && start >= 0 && end > start);
        String example = guide.substring(start + 8, end);
        int boundary = example.indexOf("\n\n");
        assertTrue(boundary > 0);
        String source = example.substring(0, boundary) + "\nfinal class Example { static void register() {\n"
                + example.substring(boundary + 2) + "\n}}";
        Path file = output.resolve("Example.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var diagnostics = new ByteArrayOutputStream();
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, diagnostics, "-proc:none", "-encoding", "UTF-8",
                "-classpath", System.getProperty("shbui.examples.compileClasspath"), "-d", output.toString(), file.toString());
        assertEquals(0, result, () -> diagnostics.toString(StandardCharsets.UTF_8));
    }
}
