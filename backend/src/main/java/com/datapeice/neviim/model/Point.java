package com.datapeice.neviim.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "points", indexes = {
        @Index(name = "idx_point_lat_lng", columnList = "latitude, longitude"),
        @Index(name = "idx_point_type", columnList = "type"),
        @Index(name = "idx_point_country", columnList = "countryCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Point {

    @Id
    @Column(length = 64)
    private String name;

    private double latitude;
    private double longitude;

    @Column(length = 32)
    private String type;

    @Column(length = 8)
    private String countryCode;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String street;

    @Column(length = 512)
    private String locationDescription;

}
