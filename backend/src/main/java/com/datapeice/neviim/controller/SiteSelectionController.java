package com.datapeice.neviim.controller;

import com.datapeice.neviim.dto.SiteSelectionRequest;
import com.datapeice.neviim.dto.SiteSelectionResultDto;
import com.datapeice.neviim.service.SiteSelectionService;
import com.datapeice.neviim.service.OverpassPOIService;
import com.datapeice.neviim.dto.PointDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/site-selection")
@RequiredArgsConstructor
@Slf4j
public class SiteSelectionController {

    private final SiteSelectionService siteSelectionService;
    private final OverpassPOIService overpassPOIService;

    @PostMapping("/analyze")
    public ResponseEntity<SiteSelectionResultDto> analyze(@RequestBody SiteSelectionRequest request) {
        log.info("Site selection analysis requested for bbox: [{}, {}] to [{}, {}]",
                request.getMinLat(), request.getMinLng(),
                request.getMaxLat(), request.getMaxLng());

        if (request.getMinLat() >= request.getMaxLat() || request.getMinLng() >= request.getMaxLng()) {
            return ResponseEntity.badRequest().build();
        }

        double latSpan = request.getMaxLat() - request.getMinLat();
        double lngSpan = request.getMaxLng() - request.getMinLng();
        if (latSpan > 1.0 || lngSpan > 1.0) {
            log.warn("Bounding box too large: {}° x {}° — capping at 1° x 1°", latSpan, lngSpan);
            return ResponseEntity.badRequest().body(
                    SiteSelectionResultDto.builder()
                            .areaName("Area too large — please select a smaller region (max ~100km x 100km)")
                            .totalCandidates(0)
                            .build()
            );
        }

        SiteSelectionResultDto result = siteSelectionService.analyze(
                request.getMinLat(), request.getMaxLat(), request.getMinLng(), request.getMaxLng(),
                request.getGreenfieldBonus(), request.getAccess247Bonus(), request.getGreenfieldRadiusKm(),
                request.getCompetitorWeight()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/competitors")
    public ResponseEntity<List<PointDto>> getCompetitors(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng
    ) {
        if (maxLat - minLat > 1.0 || maxLng - minLng > 1.0) {
            return ResponseEntity.badRequest().build();
        }
        
        List<OverpassPOIService.POI> pois = overpassPOIService.fetchCompetitorsInBbox(minLat, maxLat, minLng, maxLng);
        
        List<PointDto> result = pois.stream()
                .filter(p -> p.getBrand() == null || !p.getBrand().toLowerCase().contains("inpost"))
                .map(p -> PointDto.builder()
                        .name(p.getBrand() != null ? p.getBrand() : "Competitor Locker")
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .type("competitor")
                        .locationDescription(p.getName() != null ? p.getName() : p.getType())
                        .build())
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(result);
    }
}
