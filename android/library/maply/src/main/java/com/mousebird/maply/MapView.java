/*  MapView.java
 *  WhirlyGlobeLib
 *
 *  Created by Steve Gifford on 6/2/14.
 *  Copyright 2011-2021 mousebird consulting
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.mousebird.maply;

import android.app.Activity;

import java.lang.ref.WeakReference;

/**
 * The Map View handles math related to user position and orientation.
 * It's largely opaque to toolkit users.  The MaplyController handles
 * passing data to and getting data from it.
 * 
 */
public class MapView extends View
{	
	protected MapView() {
		control = new WeakReference<>(null);
	}

	protected final WeakReference<MapController> control;
	//double lastUpdated = 0.0;
	
	MapView(MapController inControl,CoordSystemDisplayAdapter inCoordAdapter)
	{
		control = new WeakReference<>(inControl);
		coordAdapter = inCoordAdapter;
		initialise(coordAdapter);
	}
	
	/**
	 * Make a copy of this MapView and return it.
	 * Handles the native side stuff
	 */
	protected MapView clone()
	{
		MapView that = new MapView(control.get(),coordAdapter);
		nativeClone(that);
		return that;
	}

	public void finalize()
	{
		dispose();
	}
	
	// Return a view state for this Map View
	@Override public ViewState makeViewState(RenderController renderer)
	{
		return new MapViewState(this,renderer);
	}
		
	// These are viewpoint animations
	interface AnimationDelegate
	{
		// Called to update the view every frame
		void updateView(MapView view);
	}
	
	AnimationDelegate animationDelegate = null;
	
	// Set the animation delegate.  Called every frame.
	void setAnimationDelegate(AnimationDelegate delegate)
	{
		if (delegate == null) {
			cancelAnimation();
			return;
		}
		synchronized (this) {
			animationDelegate = delegate;
			MapController theControl = control.get();
			if (theControl != null) {
				theControl.handleStartMoving(false);
			}
		}
	}
	
	// Clear the animation delegate
	@Override public void cancelAnimation() 
	{
		boolean didStop;
		synchronized (this) {
			didStop = (animationDelegate != null);
			animationDelegate = null;
		}

		if (didStop) {
			MapController theControl = control.get();
			Activity activity = (theControl != null) ? theControl.getActivity() : null;
			if (activity != null) {
				activity.runOnUiThread(() -> {
					MapController theControl2 = control.get();
					if (theControl2 != null) {
						theControl2.handleStopMoving(false);
					}
				});
			}
		}
	}
	
	// Called on the rendering thread right before we render
	@Override public void animate() 
	{
		boolean doRunViewUpdates = false;

		synchronized (this) {
			if (animationDelegate != null) {
				animationDelegate.updateView(this);
				doRunViewUpdates = true;
			}
		}

		// Note: This is probably the wrong thread
		if (doRunViewUpdates)
			runViewUpdates();
	}

	// True if animation is in progress
	public boolean isAnimating()
	{
		synchronized (this) {
			return (animationDelegate != null);
		}
	}

	// Set the view location from a Point3d
	void setLoc(Point3d loc)
	{
		setLoc(loc,true);
	}

	// Set the view location from a Point3d
	void setLoc(Point3d loc, boolean runViewUpdates)
	{
		double z = loc.getZ();
		z = Math.min(maxHeightAboveSurface(), z);
		z = Math.max(minHeightAboveSurface(), z);

		setLoc(loc.getX(),loc.getY(),z);

		if (runViewUpdates) {
			runViewUpdates();
		}
	}

	// Calculate the point on the view plane given the screen location
	native Point3d pointOnPlaneFromScreen(Point2d screenPt,Matrix4d viewModelMatrix,Point2d frameSize,boolean clip);
	// Calculate the point on the screen from a point on the view plane
	native Point2d pointOnScreenFromPlane(Point3d pt,Matrix4d viewModelMatrix,Point2d frameSize);
	// Minimum possible height above the surface
	native double minHeightAboveSurface();
	// Maximum possible height above the surface
	native double maxHeightAboveSurface();
	// Set the view location (including height)
	private native void setLoc(double x,double y,double z);
	// Get the current view location
	public native Point3d getLoc();
	// Set the 2D rotation
	native void setRot(double rot);
	// Return the 2D rotation
	native double getRot();

	static
	{
		nativeInit();
	}
	private static native void nativeInit();
	native void initialise(CoordSystemDisplayAdapter coordAdapter);
	// Make a copy of this map view and return it
	protected native void nativeClone(MapView dest);
	native void dispose();
}
