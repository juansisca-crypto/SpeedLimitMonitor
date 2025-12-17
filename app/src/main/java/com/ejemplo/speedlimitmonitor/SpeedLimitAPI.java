package com.ejemplo.speedlimitmonitor;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

public class SpeedLimitAPI {
    
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
    
    private static int lastKnownLimit = 60;
    private static double lastLat = 0;
    private static double lastLon = 0;
    
    public static int getSpeedLimit(double lat, double lon) {
        try {
            String query = String.format(
                "[out:json][timeout:5];(way(around:100,%f,%f)[\"maxspeed\"];);out body;",
                lat, lon
            );
            
            String url = "https://overpass-api.de/api/interpreter?data=" + 
                         java.net.URLEncoder.encode(query, "UTF-8");
            
            Request request = new Request.Builder()
                .url(url)
                .build();
            
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                return -1;
            }
            
            String jsonData = response.body().string();
            JSONObject json = new JSONObject(jsonData);
            JSONArray elements = json.getJSONArray("elements");
            
            if (elements.length() > 0) {
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);
                    if (element.has("tags")) {
                        JSONObject tags = element.getJSONObject("tags");
                        if (tags.has("maxspeed")) {
                            String maxspeed = tags.getString("maxspeed");
                            
                            if (maxspeed.matches("\\d+")) {
                                return Integer.parseInt(maxspeed);
                            } else if (maxspeed.contains("mph")) {
                                int mph = Integer.parseInt(maxspeed.replaceAll("[^0-9]", ""));
                                return (int)(mph * 1.609);
                            } else if (maxspeed.equals("ES:urban")) {
                                return 50;
                            } else if (maxspeed.equals("ES:rural")) {
                                return 90;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return -1;
    }
    
    public static int getSpeedLimitWithCache(double lat, double lon) {
        double distance = calculateDistance(lastLat, lastLon, lat, lon);
        
        if (distance > 0.2) {
            int newLimit = getSpeedLimit(lat, lon);
            if (newLimit > 0) {
                lastKnownLimit = newLimit;
                lastLat = lat;
                lastLon = lon;
            }
        }
        
        return lastKnownLimit;
    }
    
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadius * c;
    }
}
