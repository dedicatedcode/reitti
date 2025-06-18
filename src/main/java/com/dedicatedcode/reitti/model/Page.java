package com.dedicatedcode.reitti.model;

import java.util.List;

public class Page<T> {
    private final List<T> content;
    private final PageRequest pageable;
    private final long totalElements;
    
    public Page(List<T> content, PageRequest pageable, long totalElements) {
        this.content = content;
        this.pageable = pageable;
        this.totalElements = totalElements;
    }
    
    public List<T> getContent() {
        return content;
    }
    
    public PageRequest getPageable() {
        return pageable;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public int getTotalPages() {
        return (int) Math.ceil((double) totalElements / pageable.getPageSize());
    }
    
    public boolean hasNext() {
        return pageable.getPageNumber() + 1 < getTotalPages();
    }
    
    public boolean hasPrevious() {
        return pageable.getPageNumber() > 0;
    }
    
    public boolean isFirst() {
        return pageable.getPageNumber() == 0;
    }
    
    public boolean isLast() {
        return !hasNext();
    }
    
    public int getNumber() {
        return pageable.getPageNumber();
    }
    
    public int getSize() {
        return pageable.getPageSize();
    }
    
    public int getNumberOfElements() {
        return content.size();
    }
}
