package com.datapeice.neviim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datapeice.neviim.model.Point;
import com.datapeice.neviim.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class InPostApiService {

    private final RestTemplate restTemplate;
    private final PointRepository pointRepository;
    private final ObjectMapper objectMapper;

    @Value("${inpost.api.points-url}")
    private String pointsUrl;

    @Value("${inpost.api.page-size:1000}")
    private int pageSize;

    @Value("${inpost.api.max-pages:200}")
    private int maxPages;

    @org.springframework.scheduling.annotation.Async
    public void ingest() {
        try {
            fetchAndSaveAllPoints();
        } catch (Exception e) {
            log.error("Background ingestion failed: {}", e.getMessage());
        }
    }

    public int fetchAndSaveAllPoints() {
        log.info("Starting InPost API data ingestion from {}", pointsUrl);
        pointRepository.deleteAll();
        log.info("Cleared old database records.");
        
        int totalPages = 1;
        try {
            String url = String.format("%s?per_page=%d&page=1", pointsUrl, pageSize);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            totalPages = root.path("total_pages").asInt(1);
        } catch (Exception e) {
            log.error("Failed to fetch initial page: {}", e.getMessage());
            return 0;
        }

        int pagesToFetch = Math.min(totalPages, maxPages);
        log.info("Will fetch {} pages in parallel", pagesToFetch);
        
        AtomicInteger totalImported = new AtomicInteger(0);

        IntStream.rangeClosed(1, pagesToFetch).parallel().forEach(page -> {
            try {
                String url = String.format("%s?per_page=%d&page=%d", pointsUrl, pageSize, page);
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.path("items");
                
                if (items.isArray() && !items.isEmpty()) {
                    List<Point> pointsBatch = new ArrayList<>();
                    for (JsonNode item : items) {
                        try {
                            Point point = parsePoint(item);
                            if (point != null) {
                                pointsBatch.add(point);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse point on page {}: {}", page, e.getMessage());
                        }
                    }
                    if (!pointsBatch.isEmpty()) {
                        synchronized (this) {
                            pointRepository.saveAll(pointsBatch);
                        }
                        totalImported.addAndGet(pointsBatch.size());
                        log.info("Imported {} points from page {}/{}", pointsBatch.size(), page, pagesToFetch);
                    }
                }
            } catch (Exception e) {
                log.error("Error fetching page {}: {}", page, e.getMessage());
            }
        });

        return totalImported.get();
    }

    private Point parsePoint(JsonNode item) {
        String name = item.path("name").asText(null);
        if (name == null) return null;

        JsonNode location = item.path("location");
        double lat = location.path("latitude").asDouble(0);
        double lng = location.path("longitude").asDouble(0);
        if (lat == 0 && lng == 0) return null;

        JsonNode address = item.path("address_details");

        String type = "unknown";
        JsonNode typeNode = item.path("type");
        if (typeNode.isArray() && !typeNode.isEmpty()) {
            type = typeNode.get(0).asText("unknown");
        } else if (typeNode.isTextual()) {
            type = typeNode.asText("unknown");
        }


        return Point.builder()
                .name(name)
                .latitude(lat)
                .longitude(lng)
                .type(type)
                .countryCode(item.path("country").asText(
                        address.path("country_code").asText(
                                item.path("address").path("country_code").asText("PL"))))
                .city(address.path("city").asText(
                        item.path("address").path("city").asText("")))
                .street(address.path("street").asText(
                        item.path("address").path("street").asText("")))
                .locationDescription(item.path("location_description").asText(""))
                .build();
    }

    // hiiiii :0
    public long getPointCount() {
        return pointRepository.count();
    }
}
