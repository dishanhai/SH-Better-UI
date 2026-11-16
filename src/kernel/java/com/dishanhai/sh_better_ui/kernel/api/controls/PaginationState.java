// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** Data pagination independent of rendering. Pages are one-based; empty data has page zero. */
public final class PaginationState {
    private long totalItems, page;
    private int pageSize;

    public PaginationState(long totalItems, int pageSize, long page) {
        if (totalItems < 0 || pageSize < 1) throw new IllegalArgumentException("Invalid pagination size");
        this.totalItems = totalItems;
        this.pageSize = pageSize;
        this.page = clamp(page);
    }
    public long totalItems() { return totalItems; }
    public int pageSize() { return pageSize; }
    public long page() { return page; }
    public long pageCount() { return totalItems == 0 ? 0 : 1 + (totalItems - 1) / pageSize; }
    public long fromIndex() { return page == 0 ? 0 : (page - 1) * pageSize; }
    /** Exclusive end of the current page. Arithmetic remains valid at Long.MAX_VALUE. */
    public long toIndex() { long from = fromIndex(); return from + Math.min(pageSize, totalItems - from); }
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < pageCount(); }
    public boolean previous() { return hasPrevious() && select(page - 1); }
    public boolean next() { return hasNext() && select(page + 1); }
    public boolean select(long target) {
        long next = clamp(target);
        if (next == page) return false;
        page = next;
        return true;
    }
    /** Data refresh, with no callback. A removed page clamps to the new last page. */
    public void setTotalItems(long totalItems) {
        if (totalItems < 0) throw new IllegalArgumentException("Negative item count");
        this.totalItems = totalItems;
        page = clamp(page);
    }
    /** Preserve the old first visible item's page when changing the page size. */
    public void setPageSize(int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("Invalid page size");
        long anchor = fromIndex();
        this.pageSize = pageSize;
        page = clamp(anchor / pageSize + 1);
    }
    private long clamp(long target) { return pageCount() == 0 ? 0 : Math.max(1, Math.min(pageCount(), target)); }
}
