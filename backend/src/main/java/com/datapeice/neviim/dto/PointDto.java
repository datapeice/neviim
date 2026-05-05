package com.datapeice.neviim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointDto {
    private String name;
    private double latitude;
    private double longitude;
    private String type;
    private String countryCode;
    private String city;
    private String street;
    private String locationDescription;
}
