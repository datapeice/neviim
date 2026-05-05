package com.datapeice.neviim.controller;

import com.datapeice.neviim.dto.PointDto;
import com.datapeice.neviim.model.Point;
import com.datapeice.neviim.repository.PointRepository;
import com.datapeice.neviim.service.InPostApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Slf4j
public class PointController {

    private final PointRepository pointRepository;
    private final InPostApiService inPostApiService;

    @GetMapping
    public ResponseEntity<List<PointDto>> getPoints(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String type) {
        List<Point> points;
        if (country != null && type != null) {
            points = pointRepository.findByCountryCodeAndType(country.toUpperCase(), type);
        } else if (country != null) {
            points = pointRepository.findByCountryCode(country.toUpperCase());
        } else if (type != null) {
            points = pointRepository.findByType(type);
        } else {
            points = pointRepository.findAll();
        }
        List<PointDto> dtos = points.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPoints", pointRepository.count());
        stats.put("countries", pointRepository.findDistinctCountries());
        Map<String, Long> byCountry = new HashMap<>();
        for (String cc : pointRepository.findDistinctCountries()) {
            byCountry.put(cc, pointRepository.countByCountryCode(cc));
        }
        stats.put("byCountry", byCountry);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/bbox")
    public ResponseEntity<List<PointDto>> getPointsInBbox(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng) {
        List<Point> points = pointRepository.findInBoundingBox(minLat, maxLat, minLng, maxLng);
        List<PointDto> dtos = points.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestFromApi() {
        log.info("Manual data ingestion triggered");
        int count = inPostApiService.fetchAndSaveAllPoints();
        Map<String, Object> result = new HashMap<>();
        result.put("imported", count);
        result.put("total", pointRepository.count());
        return ResponseEntity.ok(result);
    }

    private PointDto toDto(Point p) {
        return PointDto.builder()
                .name(p.getName())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .type(p.getType())
                .countryCode(p.getCountryCode())
                .city(p.getCity())
                .street(p.getStreet())
                .locationDescription(p.getLocationDescription())
                .estimatedCapacity(p.getEstimatedCapacity())
                .build();
    }
}
