package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActionDto {
    private int seq;
    private String type;            // "copy" | "delete" | "move"
    private String at;              // ISO‑8601 instant string

    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        extra.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
        this.at = at;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}