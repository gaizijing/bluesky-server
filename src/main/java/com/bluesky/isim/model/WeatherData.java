package com.bluesky.isim.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WeatherData {
    private BigDecimal windDirection;
    private BigDecimal windSpeed;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private BigDecimal pressure;
    private BigDecimal visibility;
    private BigDecimal cloudCover;
    private BigDecimal precipitation;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;

    private LocalDateTime timestamp;
    private String pointId;
    private String source = "BLUESKY";

    private BigDecimal turbulenceIntensity;
    private BigDecimal windShear;

    public String toIsimFormat() {
        double ws = this.windSpeed != null ? this.windSpeed.doubleValue() : 0;
        double wx = ws;
        double wy = 0;
        double wz = 0;
        return String.format("WX=%.4f;WY=%.4f;WZ=%.4f", wx, wy, wz);
    }

    public static WeatherData fromWeatherRealtime(com.bluesky.entity.WeatherRealtime weather) {
        if (weather == null) {
            return null;
        }

        WeatherData data = new WeatherData();
        data.setWindDirection(BigDecimal.valueOf(weather.getWind360()));
        data.setWindSpeed(weather.getWindSpeed());
        data.setTemperature(weather.getTemp());
        data.setHumidity(BigDecimal.valueOf(weather.getHumidity()));
        data.setPressure(weather.getPressure());
        data.setVisibility(weather.getVis());
        data.setCloudCover(BigDecimal.valueOf(weather.getCloud()));
        data.setPrecipitation(weather.getPrecip());
        data.setTimestamp(weather.getObsTime());

        return data;
    }
}