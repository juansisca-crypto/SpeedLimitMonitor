package com.ejemplo.speedlimitmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class SpeedMonitorService extends Service implements LocationListener {
    
    private static final String CHANNEL_ID = "SpeedMonitorChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private LocationManager locationManager;
    private ToneGenerator toneGenerator;
    private float currentSpeed = 0;
    private int speedLimit = 60;
    private boolean isOverSpeed = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Monitoreando velocidad..."));
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLocationUpdates();
        return START_STICKY;
    }
    
    private void startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                0,
                this
            );
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            currentSpeed = location.getSpeed() * 3.6f;
            
            updateSpeedLimit(location.getLatitude(), location.getLongitude());
            
            if (currentSpeed > speedLimit && !isOverSpeed) {
                isOverSpeed = true;
                playAlert();
                updateNotification("EXCESO DE VELOCIDAD: " + (int)currentSpeed + " km/h");
            } else if (currentSpeed <= speedLimit && isOverSpeed) {
                isOverSpeed = false;
                updateNotification("Velocidad normal: " + (int)currentSpeed + " km/h");
            } else {
                updateNotification("Velocidad: " + (int)currentSpeed + " km/h");
            }
        }
    }
    
    private void updateSpeedLimit(final double lat, final double lon) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int limit = SpeedLimitAPI.getSpeedLimit(lat, lon);
                if (limit > 0) {
                    speedLimit = limit;
                }
            }
        }).start();
    }
    
    private void playAlert() {
        for (int i = 0; i < 3; i++) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Monitor de Velocidad",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Speed Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build();
    }
    
    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(text));
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
}
