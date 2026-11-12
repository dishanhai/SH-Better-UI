// Author: dishanhai
package com.example.shbui;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageCodec;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;

public final class ExchangePage {
    public static final UiId ID = UiId.parse("example:exchange");
    public static final UiId EXCHANGE = UiId.parse("example:exchange_reward");
    public static final String BALANCE = "player.balance";
    public static final String BALANCE_LABEL = "player.balance_label";
    public static final String STATUS = "exchange.status";
    private static final ResourceBundle STRINGS = ResourceBundle.getBundle("example.exchange");

    private ExchangePage() {}

    public static UiPageDefinition define() {
        return UiPageDefinition.builder(ID)
                .title(text("title"))
                .field(BALANCE, UiValueSchema.integer(0, 100_000))
                .field(BALANCE_LABEL, UiValueSchema.text(128))
                .field(STATUS, UiValueSchema.text(128))
                .action(new UiActionDefinition(EXCHANGE, UiActionPolicy.SERVER, UiDataSchema.EMPTY))
                .root("root", "column", root -> root
                        .child("target", "text", node -> node.text(text("target")))
                        .child("balance", "text", node -> node.bind("text", BALANCE_LABEL))
                        .child("exchange", "button", node -> node.label(text("exchange")).action(EXCHANGE))
                        .child("status", "text", node -> node.bind("text", STATUS)))
                .build();
    }

    public static Map<String, UiValue> initialData(int balance) {
        return Map.of(BALANCE, new UiValue.Int(balance),
                BALANCE_LABEL, new UiValue.Text(MessageFormat.format(text("balance"), balance)),
                STATUS, new UiValue.Text(text("ready")));
    }

    static String text(String key) {
        return STRINGS.getString(key);
    }

    public static void main(String[] arguments) throws Exception {
        UiPageDefinition page = define();
        new UiPageValidator().requireValid(page, true);
        page.dataSchema().validate(initialData(100), true);
        byte[] bytes = UiPageCodec.encode(page);
        if (arguments.length > 0) {
            Files.write(Path.of(arguments[0]), bytes);
        }
        System.out.println(page.id() + " " + page.schemaVersion() + " " + bytes.length);
    }
}
