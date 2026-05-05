package com.datapeice.neviim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteSelectionRequest {
    private double minLat;
    private double maxLat;
    private double minLng;
    private double maxLng;
    
    private Integer greenfieldBonus;
    private Integer access247Bonus;
    private Double greenfieldRadiusKm;
    private Double competitorWeight;
}
