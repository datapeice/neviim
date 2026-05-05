package com.datapeice.neviim.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PopulationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    @Data
    public static class CityNode {
        private String name;
        private double lat;
        private double lon;
        private long population;
    }

    public List<CityNode> fetchPopulationCenters(String countryCode) {
        log.info("Fetching real population data for country: {} via Overpass API...", countryCode);
        String query = String.format(
            "[out:json][timeout:25];" +
            "area[\"ISO3166-1\"=\"%s\"]->.a;" +
            "(" +
            "  node[\"place\"~\"city|town\"](area.a);" +
            ");" +
            "out body;", 
            countryCode.toUpperCase()
        );

        try {
            Map<String, Object> response = restTemplate.postForObject(OVERPASS_URL, "data=" + query, Map.class);
            List<Map<String, Object>> elements = (List<Map<String, Object>>) response.get("elements");
            List<CityNode> cities = new ArrayList<>();
            for (Map<String, Object> el : elements) {
                Map<String, String> tags = (Map<String, String>) el.get("tags");
                if (tags == null) continue;
                CityNode city = new CityNode();
                city.setName(tags.getOrDefault("name", "Unknown"));
                city.setLat((Double) el.get("lat"));
                city.setLon((Double) el.get("lon"));
                String popStr = tags.get("population");
                if (popStr != null) {
                    try {
                        city.setPopulation(Long.parseLong(popStr.replaceAll("[^0-9]", "")));
                    } catch (NumberFormatException e) {
                        city.setPopulation(5000);
                    }
                } else {
                    city.setPopulation(2000);
                }
                cities.add(city);
            }
            return cities;
        } catch (Exception e) {
            log.error("Failed to fetch population data: {}", e.getMessage());
            return List.of();
        }
    }
}
