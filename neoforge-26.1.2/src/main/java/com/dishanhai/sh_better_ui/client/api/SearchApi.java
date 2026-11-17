// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex.Entry;
import java.util.List;

/** Local indexed search. Rebuild the snapshot when content or language changes; no JEI or widget dependency. This is the supported 26.x subset of the shared API. */
public final class SearchApi {
    SearchApi() {}

    public <T> SearchIndex<T> index(List<Entry<T>> entries) {
        return new SearchIndex<>(entries);
    }
}
