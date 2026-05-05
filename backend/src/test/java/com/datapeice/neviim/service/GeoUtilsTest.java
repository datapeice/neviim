package com.datapeice.neviim.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void testHaversineKm() {
        // Distance between Warsaw and Krakow is ~250km
        double lat1 = 52.2297;
        double lng1 = 21.0122;
        double lat2 = 50.0647;
        double lng2 = 19.9450;

        double distance = GeoUtils.haversineKm(lat1, lng1, lat2, lng2);
        
        // Assert distance is within reasonable range (250km +/- 5km)
        assertTrue(distance > 245 && distance < 255, "Distance should be around 250km, was " + distance);
    }

    @Test
    void testBoundingBox() {
        double lat = 52.0;
        double lng = 20.0;
        double radius = 10.0; // 10km

        double[] bbox = GeoUtils.boundingBox(lat, lng, radius);
        
        // [minLat, maxLat, minLng, maxLng]
        assertTrue(bbox[0] < lat);
        assertTrue(bbox[1] > lat);
        assertTrue(bbox[2] < lng);
        assertTrue(bbox[3] > lng);
        
        // Verify width of bbox in degrees (approx 111km per degree)
        double latWidth = bbox[1] - bbox[0];
        assertTrue(latWidth > 0.17 && latWidth < 0.19); // 20km / 111km/deg approx 0.18
    }
}
