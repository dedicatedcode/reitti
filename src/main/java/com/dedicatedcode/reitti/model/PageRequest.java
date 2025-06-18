package com.dedicatedcode.reitti.model;

public class PageRequest {
    private final int page;
    private final int size;
    private final Sort sort;
    
    public PageRequest(int page, int size) {
        this(page, size, null);
    }
    
    public PageRequest(int page, int size, Sort sort) {
        this.page = page;
        this.size = size;
        this.sort = sort;
    }
    
    public int getPageNumber() {
        return page;
    }
    
    public int getPageSize() {
        return size;
    }
    
    public long getOffset() {
        return (long) page * size;
    }
    
    public Sort getSort() {
        return sort;
    }
    
    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }
    
    public static PageRequest of(int page, int size, Sort sort) {
        return new PageRequest(page, size, sort);
    }
}
