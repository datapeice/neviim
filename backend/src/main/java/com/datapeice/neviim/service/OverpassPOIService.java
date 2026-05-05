package com.datapeice.neviim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class OverpassPOIService {

    private static final List<String> OVERPASS_MIRRORS = List.of(
        "https://osm.hpi.de/overpass/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OverpassPOIService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); 
        factory.setReadTimeout(45000);    
        this.restTemplate = new RestTemplate(factory);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class POI {
        private long osmId;
        private String name;
        private String type;
        private String brand;
        private double latitude;
        private double longitude;
        private String openingHours;
        private String address;
    }

    public List<POI> fetchPOIsInBbox(double minLat, double maxLat, double minLng, double maxLng) {
        log.info("Fetching POIs from Overpass API for bbox [{}, {}, {}, {}]", minLat, minLng, maxLat, maxLng);
        String bbox = String.format(Locale.US, "%f,%f,%f,%f", minLat, minLng, maxLat, maxLng);
        String query = "[out:json][timeout:60];\n" +
                "(\n" +
                "  node[\"shop\"~\"supermarket|convenience|mall|department_store|general|kiosk|grocery\"](" + bbox + ");\n" +
                "  way[\"shop\"~\"supermarket|convenience|mall|department_store|general|kiosk|grocery\"](" + bbox + ");\n" +
                "  node[\"amenity\"~\"fuel|pharmacy|bank|post_office\"](" + bbox + ");\n" +
                "  way[\"amenity\"~\"fuel|pharmacy|bank|post_office\"](" + bbox + ");\n" +
                "  node[\"amenity\"=\"parcel_locker\"](" + bbox + ");\n" +
                "  node[\"vending\"=\"parcel_pickup\"](" + bbox + ");\n" +
                ");\n" +
                "out center body;";

        return executeQuery(query);
    }

    public List<POI> fetchCompetitorsInBbox(double minLat, double maxLat, double minLng, double maxLng) {
        String bbox = String.format(Locale.US, "%f,%f,%f,%f", minLat, minLng, maxLat, maxLng);
        String query = "[out:json][timeout:30];\n" +
                "(\n" +
                "  node[\"amenity\"=\"parcel_locker\"](" + bbox + ");\n" +
                "  node[\"vending\"=\"parcel_pickup\"](" + bbox + ");\n" +
                ");\n" +
                "out center body;";
        return executeQuery(query);
    }

    public List<POI> fetchFallbackBuildingsInBbox(double minLat, double maxLat, double minLng, double maxLng) {
        String bbox = String.format(Locale.US, "%f,%f,%f,%f", minLat, minLng, maxLat, maxLng);
        String query = "[out:json][timeout:30];\n" +
                "(\n" +
                "  node[\"shop\"](" + bbox + ");\n" +
                "  node[\"amenity\"~\"fuel|supermarket|bank|pharmacy|post_office|cafe|restaurant|community_centre|townhall\"](" + bbox + ");\n" +
                "  way[\"shop\"](" + bbox + ");\n" +
                "  way[\"amenity\"~\"fuel|supermarket|post_office|community_centre|townhall\"](" + bbox + ");\n" +
                ");\n" +
                "out center body;";
        return executeQuery(query);
    }

    public List<POI> fetchAnyBuildingsInBbox(double minLat, double maxLat, double minLng, double maxLng) {
        String bbox = String.format(Locale.US, "%f,%f,%f,%f", minLat, minLng, maxLat, maxLng);
        String query = "[out:json][timeout:30];\n" +
                "(\n" +
                "  way[\"building\"](" + bbox + ");\n" +
                "  node[\"addr:housenumber\"](" + bbox + ");\n" +
                ");\n" +
                "out center body 10;";
        return executeQuery(query);
    }

    private List<POI> executeQuery(String query) {
        Exception lastException = null;
        for (String url : OVERPASS_MIRRORS) {
            try {
                log.info("Trying Overpass API mirror: {}", url);
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Neviim-Oracle-App/1.0 (contact: support@datapeice.com)");
                headers.set("Accept", "*/*");
                headers.set("Content-Type", "application/x-www-form-urlencoded");
                HttpEntity<String> entity = new HttpEntity<>("data=" + query, headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                if (response.getBody() == null) continue;
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode elements = root.path("elements");
                if (!elements.isArray()) continue;
                List<POI> pois = new ArrayList<>();
                for (JsonNode el : elements) {
                    POI poi = parseElement(el);
                    if (poi != null) pois.add(poi);
                }
                return pois;
            } catch (Exception e) {
                log.warn("Mirror {} failed: {}", url, e.getMessage());
                lastException = e;
            }
        }
        log.error("All Overpass API mirrors failed. Last error: {}", lastException != null ? lastException.getMessage() : "Unknown");
        return List.of();
    }

    private POI parseElement(JsonNode el) {
        double lat, lng;
        String elType = el.path("type").asText("");
        if ("way".equals(elType) || "relation".equals(elType)) {
            JsonNode center = el.path("center");
            lat = center.path("lat").asDouble(0);
            lng = center.path("lon").asDouble(0);
        } else {
            lat = el.path("lat").asDouble(0);
            lng = el.path("lon").asDouble(0);
        }
        if (lat == 0 && lng == 0) return null;
        JsonNode tags = el.path("tags");
        if (tags.isMissingNode()) return null;
        String shopType = tags.path("shop").asText(null);
        String amenityType = tags.path("amenity").asText(null);
        String vendingType = tags.path("vending").asText(null);
        String type = "unknown";
        if (shopType != null) type = shopType;
        else if (vendingType != null && vendingType.equals("parcel_pickup")) type = "parcel_pickup";
        else if (amenityType != null) type = amenityType;
        String name = tags.path("name").asText(null);
        if (name == null) name = tags.path("brand").asText(type);
        String brand = tags.path("brand").asText(null);
        String hours = tags.path("opening_hours").asText(null);
        String street = tags.path("addr:street").asText("");
        String houseNumber = tags.path("addr:housenumber").asText("");
        String city = tags.path("addr:city").asText("");
        String address = buildAddress(street, houseNumber, city);
        return POI.builder()
                .osmId(el.path("id").asLong())
                .name(name)
                .type(type)
                .brand(brand)
                .latitude(lat)
                .longitude(lng)
                .openingHours(hours)
                .address(address.isEmpty() ? null : address)
                .build();
    }

    private String buildAddress(String street, String number, String city) {
        StringBuilder sb = new StringBuilder();
        if (!street.isEmpty()) {
            sb.append(street);
            if (!number.isEmpty()) sb.append(" ").append(number);
        }
        if (!city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        return sb.toString();
    }
}
