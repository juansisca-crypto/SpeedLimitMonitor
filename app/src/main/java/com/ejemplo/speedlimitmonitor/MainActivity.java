package com.ejemplo.speedlimitmonitor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements LocationListener {
    
    private static final int PERMISSION_REQUEST_CODE = 1;
    
    private LocationManager locationManager;
    private ToneGenerator toneGenerator;
    private Handler handler;
    
    private TextView speedTextView;
    private TextView speedLimitTextView;
    private TextView statusTextView;
    private ImageView statusIcon;
    private Button startStopButton;
    
    private float currentSpeed = 0;
    private int speedLimit = 60;
    private boolean isMonitoring = false;
    private boolean wasOverSpeed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        speedTextView = findViewById(R.id.speedTextView);
        speedLimitTextView = findViewById(R.id.speedLimitTextView);
        statusTextView = findViewById(R.id.statusTextView);
        statusIcon = findViewById(R.id.statusIcon);
        startStopButton = findViewById(R.id.startStopButton);
        
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        handler = new Handler(Looper.getMainLooper());
        
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMonitoring();
            }
        });
        
        checkAndRequestPermissions();
    }
    
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                }, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Sin permisos GPS, la app no funcionará", 
                             Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
    }
    
    private void startMonitoring() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Necesitas conceder permisos GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            0,
            this
        );
        
        isMonitoring = true;
        startStopButton.setText("DETENER");
        startStopButton.setBackgroundColor(Color.parseColor("#E74C3C"));
        statusTextView.setText("Buscando señal GPS...");
        
        Intent serviceIntent = new Intent(this, SpeedMonitorService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    
    private void stopMonitoring() {
        locationManager.removeUpdates(this);
        
        isMonitoring = false;
        startStopButton.setText("INICIAR MONITOREO");
        startStopButton.setBackgroundColor(Color.parseColor("#2C3E50"));
        statusTextView.setText("Detenido");
        
        Intent serviceIntent = new Intent(this, SpeedMonitorService.class);
        stopService(serviceIntent);
        
        resetUI();
    }
    
    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            currentSpeed = location.getSpeed() * 3.6f;
            
            speedTextView.setText(String.format("%.0f", currentSpeed));
            statusTextView.setText("GPS conectado");
            
            fetchSpeedLimit(location.getLatitude(), location.getLongitude());
            checkSpeedViolation();
        } else {
            statusTextView.setText("Esperando señal GPS...");
        }
    }
    
    private void fetchSpeedLimit(final double lat, final double lon) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int limit = SpeedLimitAPI.getSpeedLimitWithCache(lat, lon);
                
                if (limit > 0) {
                    speedLimit = limit;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            speedLimitTextView.setText("Límite: " + speedLimit + " km/h");
                        }
                    });
                }
            }
        }).start();
    }
    
    private void checkSpeedViolation() {
        boolean isOverSpeed = currentSpeed > speedLimit;
        
        if (isOverSpeed && !wasOverSpeed) {
            setDangerMode();
            playAlertSound();
            wasOverSpeed = true;
        } else if (!isOverSpeed && wasOverSpeed) {
            setSafeMode();
            wasOverSpeed = false;
        }
    }
    
    private void setDangerMode() {
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#FF6B6B"));
        speedTextView.setTextColor(Color.WHITE);
        speedLimitTextView.setTextColor(Color.WHITE);
        statusTextView.setText("EXCESO DE VELOCIDAD");
        statusTextView.setTextColor(Color.WHITE);
        statusIcon.setColorFilter(Color.WHITE);
    }
    
    private void setSafeMode() {
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#4ECDC4"));
        speedTextView.setTextColor(Color.parseColor("#2C3E50"));
        speedLimitTextView.setTextColor(Color.parseColor("#34495E"));
        statusTextView.setText("Velocidad normal");
        statusTextView.setTextColor(Color.parseColor("#34495E"));
        statusIcon.setColorFilter(Color.parseColor("#2C3E50"));
    }
    
    private void resetUI() {
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#4ECDC4"));
        speedTextView.setText("0");
        speedTextView.setTextColor(Color.parseColor("#2C3E50"));
        speedLimitTextView.setText("Límite: -- km/h");
        speedLimitTextView.setTextColor(Color.parseColor("#34495E"));
        statusTextView.setTextColor(Color.parseColor("#34495E"));
        statusIcon.setColorFilter(Color.parseColor("#2C3E50"));
    }
    
    private void playAlertSound() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(this, "GPS activado", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(this, "GPS desactivado", Toast.LENGTH_SHORT).show();
    }
}
