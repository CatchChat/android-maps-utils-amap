package com.amap.api.maps2d.clustering.algo;

import java.util.Collection;
import java.util.Set;

import com.amap.api.maps2d.clustering.Cluster;
import com.amap.api.maps2d.clustering.ClusterItem;

/**
 * Logic for computing clusters
 */
public interface Algorithm<T extends ClusterItem> {
    void addItem(T item);

    void addItems(Collection<T> items);

    void clearItems();

    void removeItem(T item);

    Set<? extends Cluster<T>> getClusters(double zoom);

    Collection<T> getItems();
}