package com.amap.api.maps2d.clustering;

import com.amap.api.maps2d.model.LatLng;

/**
 * ClusterItem represents a marker on the map.
 */
public interface ClusterItem {

    /**
     * The position of this marker. This must always return the same value.
     */
    LatLng getPosition();
}