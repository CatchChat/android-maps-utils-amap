package com.amap.api.maps2d.clustering;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.content.Context;
import android.os.AsyncTask;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.MarkerManager;
import com.amap.api.maps2d.clustering.algo.Algorithm;
import com.amap.api.maps2d.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.amap.api.maps2d.clustering.algo.PreCachingAlgorithmDecorator;
import com.amap.api.maps2d.clustering.view.ClusterRenderer;
import com.amap.api.maps2d.clustering.view.DefaultClusterRenderer;
import com.amap.api.maps2d.model.CameraPosition;
import com.amap.api.maps2d.model.Marker;

/**
 * Groups many items on a map based on zoom level.
 * <p/>
 * ClusterManager should be added to the map as an:
 * <ul>
 * <li>{@link AMap.OnCameraChangeListener}</li>
 * <li>{@link AMap.OnMarkerClickListener}</li>
 * </ul>
 */
public class ClusterManager<T extends ClusterItem> implements AMap.OnCameraChangeListener, AMap.OnMarkerClickListener,
		AMap.OnInfoWindowClickListener {
	private final MarkerManager mMarkerManager;
	private final MarkerManager.Collection mMarkers;
	private final MarkerManager.Collection mClusterMarkers;

	private Algorithm<T> mAlgorithm;
	private final ReadWriteLock mAlgorithmLock = new ReentrantReadWriteLock();
	private ClusterRenderer<T> mRenderer;

	private AMap mMap;
	private CameraPosition mPreviousCameraPosition;
	private ClusterTask mClusterTask;
	private final ReadWriteLock mClusterTaskLock = new ReentrantReadWriteLock();

	private OnClusterItemClickListener<T> mOnClusterItemClickListener;
	private OnClusterInfoWindowClickListener<T> mOnClusterInfoWindowClickListener;
	private OnClusterItemInfoWindowClickListener<T> mOnClusterItemInfoWindowClickListener;
	private OnClusterClickListener<T> mOnClusterClickListener;

	public ClusterManager(final Context context, final AMap map) {
		this(context, map, new MarkerManager(map));
	}

	public ClusterManager(final Context context, final AMap map, final MarkerManager markerManager) {
		mMap = map;
		mMarkerManager = markerManager;
		mClusterMarkers = markerManager.newCollection();
		mMarkers = markerManager.newCollection();
		mRenderer = new DefaultClusterRenderer<T>(context, map, this);
		mAlgorithm = new PreCachingAlgorithmDecorator<T>(new NonHierarchicalDistanceBasedAlgorithm<T>());
		mClusterTask = new ClusterTask();
		mRenderer.onAdd();
	}

	public void addItem(final T myItem) {
		mAlgorithmLock.writeLock().lock();
		try {
			mAlgorithm.addItem(myItem);
		} finally {
			mAlgorithmLock.writeLock().unlock();
		}
	}

	public void addItems(final Collection<T> items) {
		mAlgorithmLock.writeLock().lock();
		try {
			mAlgorithm.addItems(items);
		} finally {
			mAlgorithmLock.writeLock().unlock();
		}

	}

	public void clearItems() {
		mAlgorithmLock.writeLock().lock();
		try {
			mAlgorithm.clearItems();
		} finally {
			mAlgorithmLock.writeLock().unlock();
		}
	}

	/**
	 * Force a re-cluster. You may want to call this after adding new item(s).
	 */
	public void cluster() {
		mClusterTaskLock.writeLock().lock();
		try {
			// Attempt to cancel the in-flight request.
			mClusterTask.cancel(true);
			mClusterTask = new ClusterTask();
			mClusterTask.execute(mMap.getCameraPosition().zoom);
		} finally {
			mClusterTaskLock.writeLock().unlock();
		}
	}

	public MarkerManager.Collection getClusterMarkerCollection() {
		return mClusterMarkers;
	}

	public MarkerManager.Collection getMarkerCollection() {
		return mMarkers;
	}

	public MarkerManager getMarkerManager() {
		return mMarkerManager;
	}

	/**
	 * Might re-cluster.
	 * 
	 * @param cameraPosition
	 */
	@Override
	public void onCameraChange(final CameraPosition cameraPosition) {
		if (mRenderer instanceof AMap.OnCameraChangeListener) {
			((AMap.OnCameraChangeListener) mRenderer).onCameraChange(cameraPosition);
		}

		// Don't re-compute clusters if the map has just been
		// panned/tilted/rotated.
		final CameraPosition position = mMap.getCameraPosition();
		if (mPreviousCameraPosition != null && mPreviousCameraPosition.zoom == position.zoom) return;
		mPreviousCameraPosition = mMap.getCameraPosition();

		cluster();
	}

	@Override
	public void onCameraChangeFinish(final CameraPosition cameraPosition) {
		if (mRenderer instanceof AMap.OnCameraChangeListener) {
			((AMap.OnCameraChangeListener) mRenderer).onCameraChangeFinish(cameraPosition);
		}
		if (true) return;
		// Don't re-compute clusters if the map has just been
		// panned/tilted/rotated.
		final CameraPosition position = mMap.getCameraPosition();
		if (mPreviousCameraPosition != null && mPreviousCameraPosition.zoom == position.zoom) return;
		mPreviousCameraPosition = mMap.getCameraPosition();

		cluster();
	}

	@Override
	public void onInfoWindowClick(final Marker marker) {
		getMarkerManager().onInfoWindowClick(marker);
	}

	@Override
	public boolean onMarkerClick(final Marker marker) {
		return getMarkerManager().onMarkerClick(marker);
	}

	public void removeItem(final T item) {
		mAlgorithmLock.writeLock().lock();
		try {
			mAlgorithm.removeItem(item);
		} finally {
			mAlgorithmLock.writeLock().unlock();
		}
	}

	public void setAlgorithm(final Algorithm<T> algorithm) {
		mAlgorithmLock.writeLock().lock();
		try {
			if (mAlgorithm != null) {
				algorithm.addItems(mAlgorithm.getItems());
			}
			mAlgorithm = new PreCachingAlgorithmDecorator<T>(algorithm);
		} finally {
			mAlgorithmLock.writeLock().unlock();
		}
		cluster();
	}

	/**
	 * Sets a callback that's invoked when a Cluster is tapped. Note: For this
	 * listener to function, the ClusterManager must be added as a click
	 * listener to the map.
	 */
	public void setOnClusterClickListener(final OnClusterClickListener<T> listener) {
		mOnClusterClickListener = listener;
		mRenderer.setOnClusterClickListener(listener);
	}

	/**
	 * Sets a callback that's invoked when a Cluster is tapped. Note: For this
	 * listener to function, the ClusterManager must be added as a info window
	 * click listener to the map.
	 */
	public void setOnClusterInfoWindowClickListener(final OnClusterInfoWindowClickListener<T> listener) {
		mOnClusterInfoWindowClickListener = listener;
		mRenderer.setOnClusterInfoWindowClickListener(listener);
	}

	/**
	 * Sets a callback that's invoked when an individual ClusterItem is tapped.
	 * Note: For this listener to function, the ClusterManager must be added as
	 * a click listener to the map.
	 */
	public void setOnClusterItemClickListener(final OnClusterItemClickListener<T> listener) {
		mOnClusterItemClickListener = listener;
		mRenderer.setOnClusterItemClickListener(listener);
	}

	/**
	 * Sets a callback that's invoked when an individual ClusterItem's Info
	 * Window is tapped. Note: For this listener to function, the ClusterManager
	 * must be added as a info window click listener to the map.
	 */
	public void setOnClusterItemInfoWindowClickListener(final OnClusterItemInfoWindowClickListener<T> listener) {
		mOnClusterItemInfoWindowClickListener = listener;
		mRenderer.setOnClusterItemInfoWindowClickListener(listener);
	}

	public void setRenderer(final ClusterRenderer<T> view) {
		mRenderer.setOnClusterClickListener(null);
		mRenderer.setOnClusterItemClickListener(null);
		mClusterMarkers.clear();
		mMarkers.clear();
		mRenderer.onRemove();
		mRenderer = view;
		mRenderer.onAdd();
		mRenderer.setOnClusterClickListener(mOnClusterClickListener);
		mRenderer.setOnClusterInfoWindowClickListener(mOnClusterInfoWindowClickListener);
		mRenderer.setOnClusterItemClickListener(mOnClusterItemClickListener);
		mRenderer.setOnClusterItemInfoWindowClickListener(mOnClusterItemInfoWindowClickListener);
		cluster();
	}

	/**
	 * Called when a Cluster is clicked.
	 */
	public interface OnClusterClickListener<T extends ClusterItem> {
		public boolean onClusterClick(Cluster<T> cluster);
	}

	/**
	 * Called when a Cluster's Info Window is clicked.
	 */
	public interface OnClusterInfoWindowClickListener<T extends ClusterItem> {
		public void onClusterInfoWindowClick(Cluster<T> cluster);
	}

	/**
	 * Called when an individual ClusterItem is clicked.
	 */
	public interface OnClusterItemClickListener<T extends ClusterItem> {
		public boolean onClusterItemClick(T item);
	}

	/**
	 * Called when an individual ClusterItem's Info Window is clicked.
	 */
	public interface OnClusterItemInfoWindowClickListener<T extends ClusterItem> {
		public void onClusterItemInfoWindowClick(T item);
	}

	/**
	 * Runs the clustering algorithm in a background thread, then re-paints when
	 * results come back.
	 */
	private class ClusterTask extends AsyncTask<Float, Void, Set<? extends Cluster<T>>> {
		@Override
		protected Set<? extends Cluster<T>> doInBackground(final Float... zoom) {
			mAlgorithmLock.readLock().lock();
			try {
				return mAlgorithm.getClusters(zoom[0]);
			} finally {
				mAlgorithmLock.readLock().unlock();
			}
		}

		@Override
		protected void onPostExecute(final Set<? extends Cluster<T>> clusters) {
			mRenderer.onClustersChanged(clusters);
		}
	}
}
