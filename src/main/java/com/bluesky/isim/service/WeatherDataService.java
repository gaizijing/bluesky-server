package com.bluesky.isim.service;

import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.WeatherRealtime;
import com.bluesky.isim.model.WeatherData;
import com.bluesky.service.LandingPointService;
import com.bluesky.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherDataService {

    private final WeatherService weatherService;
    private final LandingPointService landingPointService;

    public WeatherData getWeatherDataByPointId(String pointId) {
        try {
            Map<String, Object> weatherResult = weatherService.getRealtimeWeather(pointId);
            if (weatherResult == null || !weatherResult.containsKey("data")) {
                log.warn("未找到起降点 {} 的气象数据", pointId);
                return createDefaultWeatherData(pointId);
            }

            WeatherRealtime weatherRealtime = (WeatherRealtime) weatherResult.get("data");
            if (weatherRealtime == null) {
                return createDefaultWeatherData(pointId);
            }

            LandingPoint point = landingPointService.getEntity(pointId);

            WeatherData weatherData = new WeatherData();
            weatherData.setWindDirection(BigDecimal.valueOf(weatherRealtime.getWind360()));
            weatherData.setWindSpeed(weatherRealtime.getWindSpeed());
            weatherData.setTemperature(weatherRealtime.getTemp());
            weatherData.setHumidity(BigDecimal.valueOf(weatherRealtime.getHumidity()));
            weatherData.setPressure(weatherRealtime.getPressure());
            weatherData.setVisibility(weatherRealtime.getVis());
            weatherData.setCloudCover(BigDecimal.valueOf(weatherRealtime.getCloud()));
            weatherData.setPrecipitation(weatherRealtime.getPrecip());
            weatherData.setTimestamp(weatherRealtime.getObsTime());
            weatherData.setPointId(pointId);

            if (point != null) {
                weatherData.setLongitude(point.getLongitude());
                weatherData.setLatitude(point.getLatitude());
                weatherData.setAltitude(point.getAltitude());
            }

            try {
                if (weatherRealtime.getWindShearLevel() != null) {
                    BigDecimal windShearValue = convertWindShearLevel(weatherRealtime.getWindShearLevel());
                    weatherData.setWindShear(windShearValue);
                }

                if (weatherRealtime.getStabilityIndex() != null) {
                    BigDecimal turbulenceValue = convertStabilityIndex(weatherRealtime.getStabilityIndex());
                    weatherData.setTurbulenceIntensity(turbulenceValue);
                }
            } catch (Exception e) {
                log.debug("无法获取湍流或风切变数据", e);
            }

            return weatherData;

        } catch (Exception e) {
            log.error("获取起降点 {} 气象数据失败", pointId, e);
            return createDefaultWeatherData(pointId);
        }
    }

    public WeatherData getLatestWeatherData() {
        try {
            List<LandingPoint> points = landingPointService.listAllEntities();
            if (points == null || points.isEmpty()) {
                log.warn("未找到可用起降点");
                return createDefaultWeatherData(null);
            }

            return getWeatherDataByPointId(points.get(0).getLandingPointId());

        } catch (Exception e) {
            log.error("获取最新气象数据失败", e);
            return createDefaultWeatherData(null);
        }
    }

    public WeatherData getWeatherDataByLocation(BigDecimal longitude, BigDecimal latitude) {
        try {
            if (longitude == null || latitude == null) {
                log.warn("经纬度参数为空，使用默认气象数据");
                return createDefaultWeatherData(null);
            }

            List<LandingPoint> allPoints = landingPointService.listAllEntities();
            if (allPoints == null || allPoints.isEmpty()) {
                log.warn("没有可用的起降点，使用默认气象数据");
                return createDefaultWeatherData(null);
            }

            LandingPoint nearestPoint = null;
            double minDistance = Double.MAX_VALUE;

            for (LandingPoint point : allPoints) {
                if (point.getLongitude() == null || point.getLatitude() == null) {
                    continue;
                }

                double distance = calculateDistance(
                    latitude.doubleValue(), longitude.doubleValue(),
                    point.getLatitude().doubleValue(), point.getLongitude().doubleValue()
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPoint = point;
                }
            }

            if (nearestPoint == null) {
                log.warn("未找到有效起降点，使用默认气象数据");
                return createDefaultWeatherData(null);
            }

            log.debug("找到最近起降点: {}, 距离: {}km", nearestPoint.getName(), minDistance);

            return getWeatherDataByPointId(nearestPoint.getLandingPointId());

        } catch (Exception e) {
            log.error("根据经纬度获取气象数据失败: longitude={}, latitude={}", longitude, latitude, e);
            return createDefaultWeatherData(null);
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private WeatherData createDefaultWeatherData(String pointId) {
        WeatherData weatherData = new WeatherData();
        weatherData.setWindDirection(BigDecimal.valueOf(45));
        weatherData.setWindSpeed(BigDecimal.valueOf(5.0));
        weatherData.setTemperature(BigDecimal.valueOf(20.0));
        weatherData.setHumidity(BigDecimal.valueOf(60));
        weatherData.setPressure(BigDecimal.valueOf(1013.0));
        weatherData.setVisibility(BigDecimal.valueOf(10000));
        weatherData.setCloudCover(BigDecimal.valueOf(30));
        weatherData.setPrecipitation(BigDecimal.valueOf(0));
        weatherData.setTimestamp(LocalDateTime.now());
        weatherData.setPointId(pointId);
        weatherData.setLongitude(BigDecimal.valueOf(120.3844));
        weatherData.setLatitude(BigDecimal.valueOf(36.1052));
        weatherData.setAltitude(BigDecimal.valueOf(100.0));

        return weatherData;
    }

    private BigDecimal convertWindShearLevel(String level) {
        if (level == null) return BigDecimal.ZERO;

        switch (level.toLowerCase()) {
            case "low": return BigDecimal.ONE;
            case "medium": return BigDecimal.valueOf(2);
            case "high": return BigDecimal.valueOf(3);
            default: return BigDecimal.ZERO;
        }
    }

    private BigDecimal convertStabilityIndex(String index) {
        if (index == null || index.isEmpty()) return BigDecimal.valueOf(3);

        char grade = index.charAt(0);
        switch (grade) {
            case 'A': return BigDecimal.ONE;
            case 'B': return BigDecimal.valueOf(2);
            case 'C': return BigDecimal.valueOf(3);
            case 'D': return BigDecimal.valueOf(4);
            case 'E': return BigDecimal.valueOf(5);
            case 'F': return BigDecimal.valueOf(6);
            default: return BigDecimal.valueOf(3);
        }
    }
}
