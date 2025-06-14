package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ImmichSearchResponse {
    
    @JsonProperty("assets")
    private AssetsResult assets;

    public AssetsResult getAssets() {
        return assets;
    }

    public void setAssets(AssetsResult assets) {
        this.assets = assets;
    }

    public static class AssetsResult {
        @JsonProperty("items")
        private List<ImmichAsset> items;
        
        @JsonProperty("total")
        private int total;

        public List<ImmichAsset> getItems() {
            return items;
        }

        public void setItems(List<ImmichAsset> items) {
            this.items = items;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }
}
