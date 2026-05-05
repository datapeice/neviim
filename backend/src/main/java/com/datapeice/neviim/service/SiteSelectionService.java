package com.datapeice.neviim.service;

import com.datapeice.neviim.dto.POICandidateDto;
import com.datapeice.neviim.dto.PointDto;
import com.datapeice.neviim.dto.SiteSelectionResultDto;
import com.datapeice.neviim.model.Point;
import com.datapeice.neviim.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteSelectionService {

    private final OverpassPOIService overpassPOIService;
    private final PointRepository pointRepository;
    private final GooglePlacesService googlePlacesService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public SiteSelectionService(PointRepository pointRepository, 
                                OverpassPOIService overpassPOIService,
                                GooglePlacesService googlePlacesService,
                                SimpMessagingTemplate messagingTemplate) {
        this.pointRepository = pointRepository;
        this.overpassPOIService = overpassPOIService;
        this.googlePlacesService = googlePlacesService;
        this.messagingTemplate = messagingTemplate;
    }

    private static final int DIST_SCORE_GT_1KM     = 100;
    private static final int DIST_SCORE_GT_500M     = 70;
    private static final int DIST_SCORE_GT_200M     = 30;
    private static final int DIST_SCORE_LT_100M     = -50;

    private static final Map<String, Integer> TYPE_SCORES = Map.ofEntries(
            Map.entry("mall",              60),
            Map.entry("department_store",  55),
            Map.entry("supermarket",       50),
            Map.entry("fuel",              40),
            Map.entry("convenience",       30),
            Map.entry("general",           25),
            Map.entry("pharmacy",          20),
            Map.entry("kiosk",             15)
    );

    private static final Map<String, Integer> BRAND_SCORES = Map.ofEntries(
            Map.entry("biedronka",   30),
            Map.entry("lidl",        30),
            Map.entry("kaufland",    28),
            Map.entry("carrefour",   28),
            Map.entry("auchan",      28),
            Map.entry("żabka",       25),
            Map.entry("zabka",       25),
            Map.entry("netto",       22),
            Map.entry("dino",        22),
            Map.entry("lewiatan",    18),
            Map.entry("stokrotka",   18),
            Map.entry("intermarche", 18),
            Map.entry("aldi",        28),
            Map.entry("tesco",       28),
            Map.entry("edeka",       25),
            Map.entry("rewe",        25),
            Map.entry("penny",       22),
            Map.entry("monoprix",    25),
            Map.entry("leclerc",     28),
            Map.entry("mercadona",   28),
            Map.entry("spar",        22),
            Map.entry("coop",        20),
            Map.entry("orlen",       25),
            Map.entry("shell",       25),
            Map.entry("bp",          25),
            Map.entry("total",       22),
            Map.entry("circle k",    22)
    );

    private static final int DEFAULT_GREENFIELD_BONUS = 50;
    private static final int DEFAULT_ACCESS_24_7_BONUS = 25;
    private static final double DEFAULT_GREENFIELD_RADIUS_KM = 5.0;

    public SiteSelectionResultDto analyze(double minLat, double maxLat, double minLng, double maxLng,
                                          Integer reqGreenfieldBonus, Integer reqAccessBonus, Double reqGreenfieldRadius,
                                          Double reqCompetitorWeight) {
        log.info("=== Site Selection Analysis: [{}, {}] to [{}, {}] ===", minLat, minLng, maxLat, maxLng);

        int greenfieldBonus = reqGreenfieldBonus != null ? reqGreenfieldBonus : DEFAULT_GREENFIELD_BONUS;
        int accessBonus = reqAccessBonus != null ? reqAccessBonus : DEFAULT_ACCESS_24_7_BONUS;
        double greenfieldRadius = reqGreenfieldRadius != null ? reqGreenfieldRadius : DEFAULT_GREENFIELD_RADIUS_KM;
        double competitorWeight = reqCompetitorWeight != null ? reqCompetitorWeight : 0.5;
        double searchBufferDeg = greenfieldRadius / 111.0;

        List<OverpassPOIService.POI> allPois = overpassPOIService.fetchPOIsInBbox(minLat, maxLat, minLng, maxLng);
        boolean isRuralMode = false;
        
        if (allPois.isEmpty()) {
            log.info("No commercial POIs found. Trying public buildings...");
            allPois = overpassPOIService.fetchFallbackBuildingsInBbox(minLat, maxLat, minLng, maxLng);
            isRuralMode = true;
        }

        if (allPois.isEmpty()) {
            log.info("No public buildings found. Trying any residential buildings...");
            allPois = overpassPOIService.fetchAnyBuildingsInBbox(minLat, maxLat, minLng, maxLng);
            isRuralMode = true;
        }

        if (allPois.isEmpty()) {
            return SiteSelectionResultDto.builder().areaName("No buildings or addressable locations found in this area").build();
        }
        
        List<OverpassPOIService.POI> competitorPois = allPois.stream()
                .filter(p -> "parcel_locker".equals(p.getType()) || "parcel_pickup".equals(p.getType()))
                .filter(p -> p.getBrand() == null || !p.getBrand().toLowerCase().contains("inpost"))
                .collect(Collectors.toList());
                
        List<OverpassPOIService.POI> pois = allPois.stream()
                .filter(p -> !"parcel_locker".equals(p.getType()) && !"parcel_pickup".equals(p.getType()))
                .collect(Collectors.toList());
                
        log.info("Found {} commercial POIs and {} competitor lockers via Overpass", pois.size(), competitorPois.size());

        List<Point> nearbyLockers = pointRepository.findInBoundingBox(
                minLat - searchBufferDeg,
                maxLat + searchBufferDeg,
                minLng - searchBufferDeg,
                maxLng + searchBufferDeg
        );
        
        List<Point> strictLockers = pointRepository.findInBoundingBox(minLat, maxLat, minLng, maxLng);
        
        log.info("Found {} buffered lockers for scoring, {} strict lockers inside area", nearbyLockers.size(), strictLockers.size());

        boolean isGreenfield = strictLockers.isEmpty();
        if (isGreenfield) {
            log.info("⚡ GREENFIELD DETECTED — no existing InPost lockers in area");
        }

        List<POICandidateDto> candidates = new ArrayList<>();
        int count = 0;
        for (OverpassPOIService.POI poi : pois) {
            POICandidateDto candidate = scorePOI(poi, nearbyLockers, competitorPois, isGreenfield, greenfieldBonus, accessBonus, greenfieldRadius, competitorWeight);
            candidate.setRank(++count);
            candidates.add(candidate);
            
            if (count % 5 == 0 || count == pois.size()) {
                messagingTemplate.convertAndSend("/topic/analysis-progress", 
                    Map.of("type", "PARTIAL_RESULTS", "candidates", candidates, "progress", (count * 100 / pois.size())));
            }
        }

        candidates.sort(Comparator.comparingInt(POICandidateDto::getTotalScore).reversed());

        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setRank(i + 1);
        }

        List<POICandidateDto> topCandidates = candidates.stream()
                .limit(20)
                .collect(Collectors.toList());

        List<PointDto> lockerDtos = strictLockers.stream()
                .map(p -> PointDto.builder()
                        .name(p.getName())
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .type(p.getType())
                        .countryCode(p.getCountryCode())
                        .city(p.getCity())
                        .street(p.getStreet())
                        .locationDescription(p.getLocationDescription())
                        .estimatedCapacity(p.getEstimatedCapacity())
                        .build())
                .collect(Collectors.toList());

        String areaName = determineAreaName(pois, strictLockers);

        List<PointDto> competitorDtos = competitorPois.stream()
                .map(p -> PointDto.builder()
                        .name(p.getBrand() != null ? p.getBrand() : "Competitor Locker")
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .type("competitor")
                        .locationDescription(p.getName() != null ? p.getName() : p.getType())
                        .build())
                .collect(Collectors.toList());

        log.info("=== Analysis complete: {} candidates scored, top score: {} ===",
                candidates.size(),
                topCandidates.isEmpty() ? "N/A" : topCandidates.get(0).getTotalScore());

        double optLat = 0;
        double optLng = 0;
        
        if (isRuralMode && !topCandidates.isEmpty()) {
            optLat = topCandidates.stream().mapToDouble(POICandidateDto::getLatitude).average().orElse(0);
            optLng = topCandidates.stream().mapToDouble(POICandidateDto::getLongitude).average().orElse(0);
        }

        return SiteSelectionResultDto.builder()
                .totalCandidates(pois.size())
                .existingLockers(strictLockers.size())
                .greenfield(isGreenfield)
                .areaName(areaName)
                .minLat(minLat)
                .maxLat(maxLat)
                .minLng(minLng)
                .maxLng(maxLng)
                .optimalLat(optLat)
                .optimalLng(optLng)
                .ruralMode(isRuralMode)
                .candidates(topCandidates)
                .nearbyLockers(lockerDtos)
                .competitorLockers(competitorDtos)
                .build();
    }

    private POICandidateDto scorePOI(OverpassPOIService.POI poi, 
                                     List<Point> nearbyInPost, 
                                     List<OverpassPOIService.POI> nearbyCompetitors,
                                     boolean isGreenfield, 
                                     int greenfieldBonusScore, 
                                     int access247BonusScore, 
                                     double greenfieldRadiusKm,
                                     double competitorPenaltyWeight) {
        double nearestDist = -1;
        if (!nearbyInPost.isEmpty()) {
            nearestDist = nearbyInPost.stream()
                    .mapToDouble(p -> GeoUtils.haversineKm(
                            poi.getLatitude(), poi.getLongitude(),
                            p.getLatitude(), p.getLongitude()))
                    .min()
                    .orElse(-1);
        }

        int distScore;
        if (nearestDist < 0) {
            distScore = 50;
        } else if (nearestDist > 1.0) {
            distScore = DIST_SCORE_GT_1KM;
        } else if (nearestDist > 0.5) {
            distScore = DIST_SCORE_GT_500M;
        } else if (nearestDist > 0.2) {
            distScore = DIST_SCORE_GT_200M;
        } else if (nearestDist < 0.1) {
            distScore = DIST_SCORE_LT_100M;
        } else {
            distScore = 10;
        }

        int typeScore = TYPE_SCORES.getOrDefault(poi.getType().toLowerCase(), 10);

        int brandScore = 10;
        if (poi.getBrand() != null) {
            String brandLower = poi.getBrand().toLowerCase();
            for (Map.Entry<String, Integer> entry : BRAND_SCORES.entrySet()) {
                if (brandLower.contains(entry.getKey())) {
                    brandScore = entry.getValue();
                    break;
                }
            }
        } else if (poi.getName() != null) {
            String nameLower = poi.getName().toLowerCase();
            for (Map.Entry<String, Integer> entry : BRAND_SCORES.entrySet()) {
                if (nameLower.contains(entry.getKey())) {
                    brandScore = entry.getValue();
                    break;
                }
            }
        }

        int accessScore = 0;
        if (poi.getOpeningHours() != null) {
            String hours = poi.getOpeningHours().toLowerCase();
            if (hours.contains("24/7") || hours.contains("24 hours")) {
                accessScore += access247BonusScore;
            }
        }
        if ("fuel".equals(poi.getType())) {
            accessScore = Math.max(accessScore, 15);
        }

        int gfBonus = 0;
        if (isGreenfield) {
            gfBonus = greenfieldBonusScore;
        } else if (nearestDist > greenfieldRadiusKm) {
            gfBonus = greenfieldBonusScore / 2;
        }

        double competitorPenalty = 0;
        if (!nearbyCompetitors.isEmpty()) {
            double nearestCompDist = Double.MAX_VALUE;
            for (OverpassPOIService.POI comp : nearbyCompetitors) {
                double d = GeoUtils.haversineKm(poi.getLatitude(), poi.getLongitude(), comp.getLatitude(), comp.getLongitude());
                if (d < nearestCompDist) nearestCompDist = d;
            }
            
            if (nearestCompDist < 0.5) {
                competitorPenalty = (1.0 - (nearestCompDist / 0.5)) * 50 * competitorPenaltyWeight;
            }
        }

        int totalScore = distScore + typeScore + brandScore + accessScore + gfBonus - (int)competitorPenalty;

        String reason = buildReason(poi, nearestDist, isGreenfield, totalScore);

        return POICandidateDto.builder()
                .name(poi.getName())
                .type(poi.getType())
                .brand(poi.getBrand())
                .latitude(poi.getLatitude())
                .longitude(poi.getLongitude())
                .address(poi.getAddress())
                .totalScore(totalScore)
                .distanceScore(distScore)
                .typeScore(typeScore)
                .brandScore(brandScore)
                .accessScore(accessScore)
                .greenfieldBonus(gfBonus)
                .distanceToNearestLockerKm(Math.round(nearestDist * 100.0) / 100.0)
                .reason(reason)
                .openingHours(poi.getOpeningHours())
                .build();
    }

    private String buildReason(OverpassPOIService.POI poi, double nearestDist, boolean isGreenfield, int score) {
        List<String> reasons = new ArrayList<>();

        String typeLabel = formatType(poi.getType());
        if (poi.getBrand() != null) {
            reasons.add(String.format("%s (%s) — high foot traffic chain", poi.getBrand(), typeLabel));
        } else {
            reasons.add(String.format("%s — %s", poi.getName() != null ? poi.getName() : "Unknown", typeLabel));
        }

        if (nearestDist < 0) {
            reasons.add("No existing InPost lockers in area (virgin territory)");
        } else if (nearestDist > 1.0) {
            reasons.add(String.format("%.0fm from nearest locker — underserved zone", nearestDist * 1000));
        } else if (nearestDist > 0.5) {
            reasons.add(String.format("%.0fm from nearest locker — moderate gap", nearestDist * 1000));
        } else if (nearestDist < 0.1) {
            reasons.add(String.format("Only %.0fm from existing locker — low need", nearestDist * 1000));
        }

        if (isGreenfield) {
            reasons.add("🌱 Greenfield — new market opportunity");
        }

        if (poi.getOpeningHours() != null && poi.getOpeningHours().toLowerCase().contains("24/7")) {
            reasons.add("24/7 access available");
        }

        return String.join(". ", reasons);
    }

    private String formatType(String type) {
        return switch (type) {
            case "supermarket"       -> "Supermarket";
            case "convenience"       -> "Convenience store";
            case "fuel"              -> "Gas station";
            case "mall"              -> "Shopping mall";
            case "department_store"  -> "Department store";
            case "pharmacy"          -> "Pharmacy";
            case "general"           -> "General store";
            case "kiosk"             -> "Kiosk";
            default                  -> type;
        };
    }

    private String determineAreaName(List<OverpassPOIService.POI> pois, List<Point> lockers) {
        if (!lockers.isEmpty()) {
            return lockers.stream()
                    .filter(p -> p.getCity() != null && !p.getCity().isBlank())
                    .collect(Collectors.groupingBy(Point::getCity, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Unknown Area");
        }

        for (OverpassPOIService.POI poi : pois) {
            if (poi.getAddress() != null && poi.getAddress().contains(",")) {
                String[] parts = poi.getAddress().split(",");
                return parts[parts.length - 1].trim();
            }
        }

        return "Unexplored Territory";
    }
}
