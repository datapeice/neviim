package com.datapeice.neviim.repository;

import com.datapeice.neviim.model.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointRepository extends JpaRepository<Point, String> {

    List<Point> findByCountryCode(String countryCode);

    List<Point> findByType(String type);

    @Query("SELECT p FROM Point p WHERE p.countryCode = :country AND p.type = :type")
    List<Point> findByCountryCodeAndType(@Param("country") String country, @Param("type") String type);

    @Query("SELECT p FROM Point p WHERE " +
            "p.latitude BETWEEN :minLat AND :maxLat AND " +
            "p.longitude BETWEEN :minLng AND :maxLng")
    List<Point> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

    @Query("SELECT DISTINCT p.countryCode FROM Point p ORDER BY p.countryCode")
    List<String> findDistinctCountries();

    long countByCountryCode(String countryCode);

    long countByType(String type);
}
