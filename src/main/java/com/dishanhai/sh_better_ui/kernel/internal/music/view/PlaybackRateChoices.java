// Author: dishanhai. Native rate choices; the output changes speed and pitch together.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.List;
final class PlaybackRateChoices {
    private PlaybackRateChoices() {}
    static final List<Double> VALUES=List.of(.5,.75,1d,1.25,1.5,1.75,2d);
    static String format(double value) {return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
}
