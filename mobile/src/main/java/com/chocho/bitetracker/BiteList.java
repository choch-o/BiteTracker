package com.chocho.bitetracker;

import java.util.ArrayList;

public class BiteList extends ArrayList<Bite> {
    final static String TAG = "BiteList";
    private long timeId;
    public BiteList() {}
    public BiteList(long timeId) {
        this.timeId = timeId;
    }

    public int getFoodCount(String food) {
        int count = 0;
        for (Bite b : this) {
            if (b.food.equals(food)) {
                count++;
            }
        }
        return count;
    }
}
