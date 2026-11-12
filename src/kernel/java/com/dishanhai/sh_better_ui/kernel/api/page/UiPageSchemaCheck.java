// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline validation of a binary schema definition against the built-in component catalog. */
public final class UiPageSchemaCheck {
    private UiPageSchemaCheck() {}

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !args[1].equals("--remote"))) {
            System.err.println("Usage: UiPageSchemaCheck <page-file> [--remote]");
            System.exit(2);
            return;
        }
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            UiPageDefinition page = UiPageCodec.decode(input.readNBytes(UiPageCodec.MAX_ENCODED_BYTES + 1));
            List<UiPageProblem> problems = new UiPageValidator().validate(page, args.length == 2);
            if (!problems.isEmpty()) {
                problems.forEach(System.err::println);
                System.exit(1);
            } else {
                System.out.println(page.id() + " schema " + page.schemaVersion() + " is valid");
            }
        } catch (IllegalArgumentException | IOException failure) {
            System.err.println("Could not read a valid page definition: " + failure.getMessage());
            System.exit(1);
        }
    }
}
