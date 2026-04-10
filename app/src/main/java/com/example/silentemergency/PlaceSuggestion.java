package com.example.silentemergency;

public class PlaceSuggestion {

    public String name;
    public double lat;
    public double lon;

    public PlaceSuggestion(String name, double lat, double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public String toString() {
        return name;
    }
}