package com.chocho.bitetracker;

public class Food {
    String position;
    String name;
    float ymin;
    float xmin;
    float ymax;
    float xmax;
    Food() {}
    Food(String position, String name, float ymin, float xmin, float ymax, float xmax) {
        this.position = position;
        this.name = name;
        this.ymin = ymin;
        this.xmin = xmin;
        this.ymax = ymax;
        this.xmax = xmax;
    }
}
