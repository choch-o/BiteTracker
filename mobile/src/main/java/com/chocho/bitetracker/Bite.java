package com.chocho.bitetracker;

public class Bite {
    String food;
    String timestamp;

    Bite(String food, String timestamp) {
        this.food = food;
        this.timestamp = timestamp;
    }

    public String getFood() {
        return food;
    }

    public String getTimestamp() {
        return timestamp;
    }

}
