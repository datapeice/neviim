package com.datapeice.neviim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GooglePlacesService {

    @Value("${google.api.key:YOUR_API_KEY_HERE}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NEARBY_SEARCH_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";
    private static final String PHOTO_URL = "https://maps.googleapis.com/maps/api/place/photo";

    public List<OverpassPOIService.POI> fetchPlaces(double lat, double lng, double radiusInMeters) {
        if ("YOUR_API_KEY_HERE".equals(apiKey)) {
            log.error("Google API Key is not configured!");
            return List.of();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(NEARBY_SEARCH_URL)
                    .queryParam("location", lat + "," + lng)
                    .queryParam("radius", radiusInMeters)
                    .queryParam("type", "store|gas_station|supermarket|pharmacy")
                    .queryParam("key", apiKey)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            
            List<OverpassPOIService.POI> results = new ArrayList<>();
            JsonNode resultsNode = root.path("results");
            
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    String photoRef = "";
                    JsonNode photos = node.path("photos");
                    if (photos.isArray() && photos.size() > 0) {
                        photoRef = photos.get(0).path("photo_reference").asText();
                    }

                    OverpassPOIService.POI poi = OverpassPOIService.POI.builder()
                            .name(node.path("name").asText())
                            .type(node.path("types").get(0).asText())
                            .latitude(node.path("geometry").path("location").path("lat").asDouble())
                            .longitude(node.path("geometry").path("location").path("lng").asDouble())
                            .brand(photoRef)
                            .address(node.path("vicinity").asText())
                            .build();
                    results.add(poi);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to fetch Google Places: {}", e.getMessage());
            return List.of();
        }
    }

    public String getPhotoUrl(String photoReference) {
        if (photoReference == null || photoReference.isEmpty()) return null;
        return PHOTO_URL + "?maxwidth=400&photo_reference=" + photoReference + "&key=" + apiKey;
    }
}
