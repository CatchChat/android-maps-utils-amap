package com.amap.api.maps2d.clustering;

import java.util.Collection;

import com.amap.api.maps2d.model.LatLng;

/**
 * A collection of ClusterItems that are nearby each other.
 */
public interface Cluster<T extends ClusterItem> {
    public LatLng getPosition();

    Collection<T> getItems();

    int getSize();
}