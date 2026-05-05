package com.datapeice.neviim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class POICandidateDto {
    private int rank;
    private String name;
    private String type;
    private String brand;
    private double latitude;
    private double longitude;
    private String address;

    private int totalScore;
    private int distanceScore;
    private int typeScore;
    private int brandScore;
    private int accessScore;
    private int greenfieldBonus;

    private double distanceToNearestLockerKm;
    private String reason;
    private String openingHours;
}
