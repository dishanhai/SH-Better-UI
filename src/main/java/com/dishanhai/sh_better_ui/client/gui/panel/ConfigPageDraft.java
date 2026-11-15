// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.NumberOption;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.Preset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

final class ConfigPageDraft<T> {
    private static final Pattern NUMBER = Pattern.compile("-?(?:[0-9]*(?:[.,][0-9]*)?)(?:[eE][+-]?[0-9]*)?");

    private final List<NumberOption<T>> options;
    private final T original;
    private final List<String> values = new ArrayList<>();
    private T baseline;

    ConfigPageDraft(ConfigPage<T> page, T original) {
        this.original = Objects.requireNonNull(original);
        baseline = original;
        options = page.options();
        for (var option : options) values.add(format(option.read().applyAsDouble(original)));
    }

    static String format(double value) {
        if (!Double.isFinite(value)) return "";
        var decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return Math.abs(decimal.scale()) > 20 ? decimal.toString() : decimal.toPlainString();
    }

    String text(int index) { return values.get(index); }

    void set(int index, String input) { values.set(index, input); }

    double value(int index) {
        try {
            String input = text(index);
            if (!NUMBER.matcher(input).matches()) return Double.NaN;
            double value = Double.parseDouble(input.replace(',', '.'));
            return options.get(index).accepts(value) ? value : Double.NaN;
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }

    int firstInvalid() {
        for (int i = 0; i < options.size(); i++) if (Double.isNaN(value(i))) return i;
        return -1;
    }

    boolean dirty() {
        for (int i = 0; i < options.size(); i++) {
            if (Double.compare(value(i), options.get(i).read().applyAsDouble(original)) != 0) return true;
        }
        return false;
    }

    boolean selected(Preset<T> preset) {
        return preset.values().entrySet().stream()
                .allMatch(entry -> Double.compare(value(options.indexOf(entry.getKey())), entry.getValue()) == 0);
    }

    void apply(Preset<T> preset) {
        preset.values().forEach((option, value) -> set(options.indexOf(option), format(value)));
    }

    boolean commit(Predicate<T> write) {
        if (firstInvalid() >= 0) return false;
        T next = original;
        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            double value = value(i);
            if (Double.compare(value, option.read().applyAsDouble(next)) != 0) {
                next = Objects.requireNonNull(option.edit().apply(next, value));
            }
        }
        if (next.equals(baseline)) return true;
        if (!write.test(next)) return false;
        baseline = next;
        return true;
    }
}
