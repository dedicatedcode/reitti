package com.dedicatedcode.reitti.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sort {
    private final List<Order> orders;
    
    public Sort(Order... orders) {
        this.orders = Arrays.asList(orders);
    }
    
    public Sort(List<Order> orders) {
        this.orders = new ArrayList<>(orders);
    }
    
    public List<Order> getOrders() {
        return orders;
    }
    
    public static Sort by(String... properties) {
        List<Order> orders = new ArrayList<>();
        for (String property : properties) {
            orders.add(Order.asc(property));
        }
        return new Sort(orders);
    }
    
    public static Sort by(Order... orders) {
        return new Sort(orders);
    }
    
    public static class Order {
        private final Direction direction;
        private final String property;
        
        public Order(Direction direction, String property) {
            this.direction = direction;
            this.property = property;
        }
        
        public Direction getDirection() {
            return direction;
        }
        
        public String getProperty() {
            return property;
        }
        
        public static Order asc(String property) {
            return new Order(Direction.ASC, property);
        }
        
        public static Order desc(String property) {
            return new Order(Direction.DESC, property);
        }
    }
    
    public enum Direction {
        ASC, DESC
    }
}
