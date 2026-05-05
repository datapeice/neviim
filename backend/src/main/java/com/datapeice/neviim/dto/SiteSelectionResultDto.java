package com.datapeice.neviim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteSelectionResultDto {
    private int totalCandidates;
    private int existingLockers;
    private boolean greenfield;
    private String areaName;
    private double minLat;
    private double maxLat;
    private double minLng;
    private double maxLng;
    private double optimalLat;
    private double optimalLng;
    private boolean ruralMode;

    private List<POICandidateDto> candidates;
    private List<PointDto> nearbyLockers;
    private List<PointDto> competitorLockers;
}
