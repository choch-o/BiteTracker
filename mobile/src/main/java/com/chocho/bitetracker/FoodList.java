package com.chocho.bitetracker;

import com.chocho.bitetracker.Food;

import java.util.ArrayList;

public class FoodList extends ArrayList<Food> {
    final static String TAG = "BiteList";
    private long timeId;
    public FoodList() {}
    public FoodList(long timeId) {
        this.timeId = timeId;
    }
}
