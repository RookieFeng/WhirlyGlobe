/*  GlobeController.java
 *  WhirlyGlobeLib
 *
 *  Created by Steve Gifford on 3/17/15.
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
import android.graphics.Color;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The GlobeController is the main object in the Maply library when using a 3D globe.  
 * Toolkit users add and remove their geometry through here.
 * <p>
 * The controller starts by creating an OpenGL ES surface and handling
 * all the various setup between Maply and that surface.  It also kicks off
 * a LayerThread, which it uses to queue requests to the rest of Maply.
 * <p>
 * Once the controller is set up and running the toolkit user can make
 * calls to add and remove geometry.  Those calls are thread safe.
 * 
 * @author sjg
 *
 */
@SuppressWarnings({"unused","UnusedReturnValue","RedundantSuppression"})
public class GlobeController extends BaseController implements View.OnTouchListener, Choreographer.FrameCallback
{
	/**
	 * Settings needed on startup so we can create the proper elements.
	 */
	public static class Settings extends BaseController.Settings
	{
		/**
		 * This is the background color to set.  We need this early so
		 * we can avoid the flashing problem.
		 */
		public int clearColor = Color.BLACK;
	}

	public GlobeController(@NotNull Activity mainActivity, @Nullable Settings settings)
	{
		super(mainActivity, settings);

		final int clearColor = ((settings != null) ? settings : new Settings()).clearColor;
		Init(clearColor);
	}

	public GlobeController(@NotNull Activity mainActivity)
	{
		super(mainActivity,null);

		Init(renderControl.clearColor);
	}

	protected void Init(int clearColor)
	{
		// Need a coordinate system to display conversion
		// For now this just sets up spherical mercator
		coordAdapter = new FakeGeocentricDisplayAdapter();

		// Create the scene and map view
		scene = new Scene(coordAdapter,renderControl);
		globeView = new GlobeView(this,coordAdapter);
		view = globeView;
		globeView.northUp = true;
		globeView.setContinuousZoom(true);
		super.setClearColor(clearColor);

		super.Init();

		if (baseView != null)
		{
			baseView.setOnTouchListener(this);
			gestureHandler = new GlobeGestureHandler(this,baseView);
		}

		// Set up some basic lights after we've started
		this.addPostSurfaceRunnable(this::resetLights);
	}
	
	@Override public void shutdown()
	{
		if (baseView != null) {
			baseView.setOnTouchListener(null);
		}

		Choreographer c = Choreographer.getInstance();
		if (c != null)
			c.removeFrameCallback(this);

		if (globeView != null) {
			globeView.cancelAnimation();
		}

		// superclass shuts down the scene

		super.shutdown();

		globeView = null;
		if (gestureHandler != null) {
			gestureHandler.shutdown();
		}
		gestureDelegate = null;
		gestureHandler = null;

		if (scene != null) {
			scene.teardownGL();
		}
	}
	
	// Map version of view
	GlobeView globeView = null;


	public GlobeView getGlobeView() {
		return globeView;
	}

	/**
	 * True if the globe is keeping north facing up on the screen.
     */
	public boolean getKeepNorthUp()
	{
		return globeView.northUp;
	}

	/**
	 * Set the keep north up parameter on or off.  On means we will always rotate the globe
	 * to keep north up.  Off means we won't.
     */
	public void setKeepNorthUp(boolean newVal)
	{
		if (globeView != null)
			globeView.northUp = newVal;
		if (gestureHandler != null)
			gestureHandler.allowRotate = !newVal;
	}

	/**
	 * Call this to allow the user to tilt with three fingers.
     */
	public void setAllowTilt(boolean allowTilt)
	{
		if (gestureHandler != null)
			gestureHandler.allowTilt = allowTilt;
	}

	/**
	 * Return the screen coordinate for a given geographic coordinate (in radians).
	 * 
	 * @param geoCoord Geographic coordinate to convert (in radians).
	 * @return Screen coordinate.
	 */
	public Point2d screenPointFromGeo(Point2d geoCoord)
	{
		return screenPointFromGeo(globeView,geoCoord);
	}

	/**
	 * Set the zoom limits for the globe.
	 * @param inMin Closest the user is allowed to zoom in.
	 * @param inMax Farthest the user is allowed to zoom out.
     */
	public void setZoomLimits(double inMin,double inMax)
	{
		if (gestureHandler != null)
			gestureHandler.setZoomLimits(inMin,inMax);
	}

	/**
	 * Get the zoom (height) limits for the globe.
	 */
	@Override
	public double getZoomLimitMin()
	{
		return (gestureHandler != null) ? gestureHandler.zoomLimitMin : 0.0;
	}

	/**
	 * Get the zoom (height) limits for the globe.
	 */
	@Override
	public double getZoomLimitMax()
	{
		return (gestureHandler != null) ? gestureHandler.zoomLimitMax : Double.POSITIVE_INFINITY;
	}

	/**
	 * Return the geographic point (radians) corresponding to the screen point.
	 * 
	 * @param screenPt Input point on the screen.
	 * @return The geographic coordinate (radians) corresponding to the screen point.
	 */
	public Point2d geoPointFromScreen(Point2d screenPt)
	{
		CoordSystemDisplayAdapter coordAdapter = globeView.getCoordAdapter();
		CoordSystem coordSys = (coordAdapter != null) ? coordAdapter.getCoordSystem() : null;
		RendererWrapper wrapper = renderWrapper;
		RenderController render = (renderWrapper != null) ? renderWrapper.maplyRender.get() : null;
		if (coordSys == null || render == null) {
			return null;
		}

		Matrix4d modelMat = globeView.calcModelViewMatrix();
		Point3d dispPt = globeView.pointOnSphereFromScreen(screenPt, modelMat, render.frameSize, false);
		if (dispPt == null)
			return null;
		Point3d localPt = coordAdapter.displayToLocal(dispPt);
		if (localPt == null)
			return null;
		Point3d geoCoord = coordSys.localToGeographic(localPt);
		if (geoCoord == null)
			return null;
		return new Point2d(geoCoord.getX(),geoCoord.getY());
	}

	/**
	 * Return the model point corresponding to a screen point.
	 *
	 * Models space is centered on the globe with a radius of 1.0.
	 *
	 * Will return nil if the screen point does not intersect the globe.
	 */
	public Point3d modelPointFromScreen(Point2d screenPt)
	{
		CoordSystemDisplayAdapter coordAdapter = globeView.getCoordAdapter();
		CoordSystem coordSys = (coordAdapter != null) ? coordAdapter.getCoordSystem() : null;
		RendererWrapper wrapper = renderWrapper;
		RenderController render = (renderWrapper != null) ? renderWrapper.maplyRender.get() : null;
		if (coordSys == null || render == null) {
			return null;
		}

		Matrix4d modelMat = globeView.calcModelViewMatrix();
		return globeView.pointOnSphereFromScreen(screenPt, modelMat, render.frameSize, false);
	}

	
	/**
	 * Returns what the user is currently looking at in geographic extents.
	 */
	public Mbr getCurrentViewGeo()
	{
		RendererWrapper wrapper = renderWrapper;
		RenderController render = (renderWrapper != null) ? renderWrapper.maplyRender.get() : null;
		if (render == null) {
			return null;
		}

		Mbr geoMbr = new Mbr();

		Point2d frameSize = render.frameSize;
		Point2d pt = geoPointFromScreen(new Point2d(0,0));
		if (pt == null) return null;
		geoMbr.addPoint(pt);

		pt = geoPointFromScreen(new Point2d(frameSize.getX(),0));
		if (pt == null) return null;
		geoMbr.addPoint(pt);

		pt = geoPointFromScreen(new Point2d(frameSize.getX(),frameSize.getY()));
		if (pt == null) return null;
		geoMbr.addPoint(pt);

		pt = geoPointFromScreen(new Point2d(0,frameSize.getY()));
		if (pt == null) return null;
		geoMbr.addPoint(pt);

		return geoMbr;
	}

	// Check if a given point and normal is facing away currently
	static double checkPointAndNormFacing(Point3d dispLoc,Point3d norm,Matrix4d mat,Matrix4d normMat)
	{
		Point4d pt = mat.multiply(new Point4d(dispLoc.getX(),dispLoc.getY(),dispLoc.getZ(),1.0));
		double x = pt.getX() / pt.getW();
		double y = pt.getY() / pt.getW();
		double z = pt.getZ() / pt.getW();
		Point4d testDir = normMat.multiply(new Point4d(norm.getX(),norm.getY(),norm.getZ(),0.0));
		Point3d pt3d = new Point3d(-x,-y,-z);
		return pt3d.dot(new Point3d(testDir.getX(),testDir.getY(),testDir.getZ()));
	}

	// Convert a geo coord to a screen point
	private Point2d screenPointFromGeo(GlobeView theGlobeView,Point2d geoCoord) {
		RendererWrapper wrapper = renderWrapper;
		RenderController render = (wrapper != null) ? wrapper.maplyRender.get() : null;
		return (render != null) ? screenPointFromGeo(theGlobeView,geoCoord,render.frameSize) : null;
	}

	// Convert a geo coord to a screen point
	private static Point2d screenPointFromGeo(GlobeView theGlobeView,Point2d geoCoord,Point2d frameSize)
	{
		CoordSystemDisplayAdapter coordAdapter = theGlobeView.getCoordAdapter();
		CoordSystem coordSys = coordAdapter.getCoordSystem();
		Point3d localPt = coordSys.geographicToLocal(new Point3d(geoCoord.getX(),geoCoord.getY(),0.0));
		if (localPt == null)
			return null;
		Point3d dispPt = coordAdapter.localToDisplay(localPt);
		if (dispPt == null)
			return null;

		Matrix4d modelMat = theGlobeView.calcModelViewMatrix();
		Matrix4d modelNormalMat = modelMat.inverse().transpose();

		if (checkPointAndNormFacing(dispPt,dispPt.normalized(),modelMat,modelNormalMat) < 0.0)
			return null;

		return theGlobeView.pointOnScreenFromSphere(dispPt, modelMat, frameSize);
	}

	boolean checkCoverage(final Mbr mbr,final GlobeView theGlobeView,final double height)
	{
		RendererWrapper wrapper = renderWrapper;
		RenderController render = (renderWrapper != null) ? renderWrapper.maplyRender.get() : null;
		if (render == null) {
			return false;
		}

		final Point2d centerLoc = mbr.middle();
		final Point3d localCoord = theGlobeView.coordAdapter.coordSys.geographicToLocal(new Point3d(centerLoc.getX(),centerLoc.getY(),0.0));
		theGlobeView.setLoc(new Point3d(localCoord.getX(),localCoord.getY(),height));

		List<Point2d> pts = mbr.asPoints();
		Point2d frameSize = render.frameSize;
		for (Point2d pt : pts)
		{
			Point2d screenPt = screenPointFromGeo(theGlobeView,pt);
			if (screenPt == null || screenPt.getX() < 0.0 || screenPt.getY() < 0.0 || screenPt.getX() > frameSize.getX() || screenPt.getY() > frameSize.getY())
				return false;
		}

		return true;
	}
	
	/**
	 * For a given position, how high do we have to be to see the given area.
	 * <p>
	 * Even for 2D maps we represent things in terms of height.
	 * 
	 * @param mbr Bounding box for the area we want to see in geographic (radians).
	 * @param pos Center of the viewing area in geographic (radians).
	 * @return Returns a height for the viewer.
	 */
	public double findHeightToViewBounds(final Mbr mbr,final Point2d pos)
	{
		// We'll experiment on a copy of the map view
		final GlobeView newGlobeView = globeView.clone();
		newGlobeView.setLoc(new Point3d(pos.getX(),pos.getY(),2.0));

		double minHeight = newGlobeView.minHeightAboveSurface();
		double maxHeight = newGlobeView.maxHeightAboveSurface();

		final boolean minOnScreen = checkCoverage(mbr,newGlobeView,minHeight);
		final boolean maxOnScreen = checkCoverage(mbr,newGlobeView,maxHeight);

		if (minOnScreen) {
			// Fits when zoomed in to max, so that's the closest we can get
			return minHeight;
		} else if (!maxOnScreen) {
			// Not visible at min or max so... what?
			return globeView.getHeight();
		}

		// Do a binary search between the two heights
		final double minRange = 1e-5;
		while (minRange < maxHeight - minHeight) {
			final double midHeight = (minHeight + maxHeight)/2.0;
			if (checkCoverage(mbr,newGlobeView,midHeight)) {
				maxHeight = midHeight;
			} else {
				minHeight = midHeight;
			}
		}

		return maxHeight;
	}

	static double EarthRadius = 6371000;

	/**
	 * Calculate a size in meters by projecting the two screen points onto the globe.
	 * Return -1, -1 if the points weren't on the globe.
	 */
	public Point2d realWorldSizeFromScreen(Point2d pt0,Point2d pt1)
	{
		Point2d size = new Point2d(-1.0,-1.0);
		if (!running || view == null || renderWrapper == null || renderWrapper.maplyRender == null || renderControl.frameSize == null)
			return size;

		final int[] frameSizeInt = getFrameBufferSize();
		final Point2d frameSize = new Point2d((double)frameSizeInt[0],(double)frameSizeInt[1]);
		final Point2d[] screenPt = new Point2d[]{
			new Point2d(pt0.getX(), pt0.getY()),
			new Point2d(pt1.getX(), pt0.getY()),
			new Point2d(pt0.getX(), pt1.getY()),
		};
		Point3d[] hits = new Point3d[3];
		for (int ii=0;ii<3;ii++) {
			final Matrix4d transform = globeView.calcModelViewMatrix();
			final Point3d hit = globeView.pointOnSphereFromScreen(screenPt[ii], transform, frameSize, true);
			if (hit == null)
				return size;
			hit.normalize();
			hits[ii] = hit;
		}

		final double da = hits[1].subtract(hits[0]).norm() * EarthRadius;
		final double db = hits[2].subtract(hits[0]).norm() * EarthRadius;

		size.setValue(da,db);
		return size;
	}

	/**
	 * This encapulates the entire view state.  You can replicate all visual parameters with
	 * set set of values.
	 */
	public static class ViewState
	{
		/**
		 * Heading from due north.  A value of MAX_VALUE means don't set it.
		 */
		public double heading = Double.MAX_VALUE;

		/**
		 * Height above the globe.  A value of MAX_VALUE means don't set it.
		 */
		public double height = Double.MAX_VALUE;

		/**
		 * Tilt as used in the view controller.
		 * A value of MAX_VALUE means don't set it.
		 */
		public double tilt = Double.MAX_VALUE;

		/**
		 * Position to move to on the globe.
		 * If null we won't set that value.
		 */
		public Point2d pos = null;

		// Note: Porting
		/// @brief If set, this is a point on the screen where pos should be.
		/// @details By default this is (-1,-1) meaning the screen position is just the middle.  Otherwise, this is where the position should wind up on the screen, if it can.
//		@property (nonatomic) CGPoint screenPos;
	}

	/**
	 * Set all the view parameters at once.
     */
	public void setViewState(ViewState viewState)
	{
		if (!isCompletelySetup())
			return;

		globeView.cancelAnimation();


		Point3d newPos = globeView.getLoc();
		if (viewState.pos != null) {
			newPos = globeView.coordAdapter.coordSys.geographicToLocal(new Point3d(viewState.pos.getX(), viewState.pos.getY(), 0.0));
		}

		double newHeight = globeView.getHeight();
		if (viewState.height != Double.MAX_VALUE)
			newHeight = viewState.height;

		if (viewState.pos != null)
			globeView.setLoc(new Point3d(newPos.getX(),newPos.getY(),newHeight));

		if (viewState.tilt != Double.MAX_VALUE)
			globeView.setTilt(viewState.tilt);

		if (viewState.heading != Double.MAX_VALUE && !getKeepNorthUp())
			globeView.setHeading(viewState.heading);
	}

	/**
	 * Return a simple description of the view state parameters.
     */
	public ViewState getViewState()
	{
		Point3d loc = globeView.getLoc();
		if (loc == null)
			return null;
		Point3d geoLoc = globeView.coordAdapter.coordSys.localToGeographic(loc);

		ViewState viewState = new ViewState();
		viewState.pos = new Point2d(geoLoc.getX(),geoLoc.getY());
		viewState.height = loc.getZ();
		viewState.heading = globeView.getHeading();
		viewState.tilt = globeView.getTilt();

		return viewState;
	}

	/**
	 * Set the current view position.
	 * @param pt Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 */
	public void setPositionGeo(final Point2d pt,final double z) {
		setPositionGeo(pt.getX(), pt.getY(), z, null);
	}

	/**
	 * Set the current view position.
	 * @param pt Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 * @param heading New heading in radians
	 */
	public void setPositionGeo(final Point2d pt,final double z,double heading) {
		setPositionGeo(pt.getX(), pt.getY(), z, heading);
	}

	/**
	 * Set the current view position.
	 * @param pt Location of the center of the screen in geographic radians (not degrees), z = height
	 */
	public void setPositionGeo(final Point3d pt) {
		setPositionGeo(pt.getX(), pt.getY(), pt.getZ(), null);
	}

	/**
	 * Set the current view position.
	 * @param pt Location of the center of the screen in geographic radians (not degrees), z = height
	 * @param heading New heading in radians
	 */
	public void setPositionGeo(final Point3d pt,double heading) {
		setPositionGeo(pt.getX(), pt.getY(), pt.getZ(), heading);
	}

	/**
	 * Set the current view position.
	 * @param x Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param y Vertical location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 */
	public void setPositionGeo(final double x,final double y,final double z) {
		setPositionGeo(x,y,z,null);
	}

	/**
	 * Set the current view position.
	 * @param x Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param y Vertical location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 * @param heading New heading in radians
	 */
	public void setPositionGeo(final double x,final double y,final double z,final Double heading) {
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> setPositionGeo(x, y, z, heading));
			}
			return;
		}

		globeView.cancelAnimation();
		Point3d geoCoord = globeView.coordAdapter.coordSys.geographicToLocal(new Point3d(x,y,0.0));
		globeView.setLoc(new Point3d(geoCoord.getX(),geoCoord.getY(),z));
		if (heading != null) {
			globeView.setHeading(heading);
		}
	}

	/**
	 * Returns the position in on the globe in terms of longitude and latitude in radians and height.
	 * Height is height above the globe.  The globe has a radius of 1.0.
     */
	public Point3d getPositionGeo()
	{
		if (!isCompletelySetup())
			return null;

		Point3d loc = globeView.getLoc();
		if (loc == null)
			return null;
		Point3d geoLoc = globeView.coordAdapter.coordSys.localToGeographic(loc);
		return new Point3d(geoLoc.getX(),geoLoc.getY(),loc.getZ());
	}

	// If true, we're all set up to run calculations
	protected boolean isCompletelySetup()
	{
		return running && globeView != null && renderWrapper != null &&
				renderWrapper.maplyRender != null && renderControl.frameSize != null;
	}
	
	/**
	 * Animate to a new view position
	 * @param x Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param y Vertical location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo(final double x,final double y,final double z,final double howLong)
	{
		animatePositionGeo(x,y,z,null,howLong);
	}

	/**
	 * Animate to a new view position
	 * @param targetGeoLoc Location of the center of the screen in geographic radians (not degrees).
	 * @param hdg New heading
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo(final Point3d targetGeoLoc,Double hdg,final double howLong) {
		animatePositionGeo(targetGeoLoc.getX(),targetGeoLoc.getY(),targetGeoLoc.getZ(),hdg,howLong);
	}

	/**
	 * Animate to a new view position
	 * @param x Horizontal location of the center of the screen in geographic radians (not degrees).
	 * @param y Vertical location of the center of the screen in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 * @param hdg New heading
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo(final double x,final double y,final double z,Double hdg,final double howLong)
	{
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> animatePositionGeo(x, y, z, howLong));
			}
			return;
		}

		hdg = globeView.northUp ? 0 : ((hdg != null) ? hdg : globeView.getHeading());

		globeView.cancelAnimation();
		Point3d geoCoord = globeView.coordAdapter.coordSys.geographicToLocal(new Point3d(x,y,0.0));
		if (geoCoord != null) {
			Quaternion newQuat = globeView.makeRotationToGeoCoord(x, y, globeView.northUp);
			if (newQuat != null) {
				globeView.setAnimationDelegate(
						new GlobeAnimateRotation(globeView, renderControl, newQuat, z,
						                         hdg, howLong, zoomAnimationEasing));
			}
		}
	}

	/**
	 * Animate to a new view position
	 * @param x Horizontal location in geographic radians (not degrees).
	 * @param y Vertical location in geographic radians (not degrees).
	 * @param z Height above the map in display units.
	 * @param offset Screen offset for the target point
	 * @param hdg New heading
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo( double x, double y, double z,Point2d offset,Double hdg, double howLong) {
		animatePositionGeo(new Point3d(x,y,z),offset,hdg,howLong);
	}

	/**
	 * Animate to a new view position
	 * @param xy Location of the in geographic radians (not degrees).
	 * @param height Height above the map in display units.
	 * @param hdg New heading
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo(Point2d xy, Double height, Double hdg, double howLong) {
		animatePositionGeo(xy.getX(), xy.getY(), height, hdg, howLong);
	}

	/**
	 * Animate to a new view position
	 * @param targetGeoLoc Location of the in geographic radians (not degrees).
	 * @param offset Screen offset for the target point
	 * @param hdg New heading
	 * @param howLong Time (in seconds) to animate.
	 */
	public void animatePositionGeo(final Point3d targetGeoLoc,final Point2d offset,Double hdg,final double howLong)
	{
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> animatePositionGeo(targetGeoLoc,offset,hdg,howLong));
			}
			return;
		}

		globeView.cancelAnimation();

		CoordSystemDisplayAdapter coordAdapter = globeView.coordAdapter;
		CoordSystem coordSys = (coordAdapter != null) ? coordAdapter.coordSys : null;
		if (coordSys == null) {
			return;
		}

		Matrix4d matrix = globeView.calcModelViewMatrix();

		int[] frameBufSizeArr = getFrameBufferSize();
		if (frameBufSizeArr == null || frameBufSizeArr.length != 2) {
			return;
		}
		Point2d frameSize = new Point2d(frameBufSizeArr[0], frameBufSizeArr[1]);

		Point2d localOffset = frameSize.multiplyBy(0.5);
		if (offset != null) {
			localOffset = localOffset.addTo(offset.multiplyBy(-1));
		}

		// check that the offset is actually within the globe, or we can't do it anyway
		Point3d offsetLoc = globeView.pointOnSphereFromScreen(localOffset, matrix, frameSize, true);
		if (offsetLoc == null) {
			return;
		}

		Point3d destPt = coordAdapter.localToDisplay(coordSys.geographicToLocal(targetGeoLoc));
		Quaternion newRotQuat = globeView.getRotQuat().multiply(new Quaternion(destPt, offsetLoc));

		if (newRotQuat != null && globeView.northUp) {
			// See where the north pole is going.
			Point3d northPole = newRotQuat.multiply(new Point3d(0,0,1)).normalized();
			if (northPole.getY() != 0.0) {
				// Not straight up, rotate it back to vertical.
				final double angle = Math.atan(northPole.getX() / northPole.getY()) + ((northPole.getY() < 0) ? Math.PI : 0);
				newRotQuat = newRotQuat.multiply(new AngleAxis(angle, destPt));
			}
		}

		if (newRotQuat != null) {
			globeView.setAnimationDelegate(
					new GlobeAnimateRotation(globeView, renderControl, newRotQuat,
					                         targetGeoLoc.getZ(), hdg, howLong, zoomAnimationEasing));
		}
	}

	/**
	 * Get the current height
	 */
	public double getHeight() {
		return isCompletelySetup() ? globeView.getHeight() : 0.0;
	}

	/**
	 * Set the view height, display units
	 */
	public void setHeight(final double height) {
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> setHeight(height));
			}
			return;
		}

		globeView.cancelAnimation();
		globeView.setHeight(height);
	}

	public double getHeading() {
		return (globeView != null) ? globeView.getHeading() : 0.0;
	}

	public void setHeading(final double heading) {
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> setHeading(heading));
			}
			return;
		}

		globeView.cancelAnimation();
		globeView.setHeading(heading);
	}

	/**
	 * This turns on an auto-rotate mode.  The globe will start rotating after a
	 * delay by the given number of degrees per second.  Very pleasant.
	 * @param autoRotateInterval Wait this number of seconds after user interaction to auto rotate.
	 * @param autoRotateDegrees Rotate this number of degrees (not radians) per second.
     */
	public void setAutoRotate(float autoRotateInterval,float autoRotateDegrees) {
		if (!isCompletelySetup()) {
			if (!rendererAttached) {
				addPostSurfaceRunnable(() -> setAutoRotate(autoRotateInterval,autoRotateDegrees));
			}
			return;
		}

		if (gestureHandler != null) {
			gestureHandler.setAutoRotate(autoRotateInterval, autoRotateDegrees);
		}
	}
	
	// Gesture handler
	GlobeGestureHandler gestureHandler = null;
	
	/**
	 * Use this delegate when you want user interface feedback from the maply controller.
	 * 
	 * @author sjg
	 *
	 */
	public interface GestureDelegate
	{
		/**
		 * The user selected the given object.  Up to you to figure out what it is.
		 * 
		 * @param globeControl The maply controller this is associated with.
		 * @param selObjs The objects the user selected (e.g. MaplyScreenMarker).
		 * @param loc The location they tapped on.  This is in radians.
		 * @param screenLoc The location on the OpenGL surface.
		 */
		void userDidSelect(GlobeController globeControl, SelectedObject[] selObjs, Point2d loc, Point2d screenLoc);
		
		/**
		 * The user tapped somewhere, but not on a selectable object.
		 * 
		 * @param globeControl The maply controller this is associated with.
		 * @param loc The location they tapped on.  This is in radians.  If null, then the user tapped outside the globe.
		 * @param screenLoc The location on the OpenGL surface.
		 */
		void userDidTap(GlobeController globeControl, Point2d loc, Point2d screenLoc);

		/**
		 * The user tapped outside of the globe.
		 *
		 * @param globeControl The maply controller this is associated with.
		 * @param screenLoc The location on the OpenGL surface.
         */
		void userDidTapOutside(GlobeController globeControl, Point2d screenLoc);

		/**
		 * The user did long press somewhere, there might be an object
		 * @param globeControl The maply controller this is associated with.
		 * @param selObjs The objects the user selected (e.g. MaplyScreenMarker) or null if there was no object.
		 * @param loc The location they tapped on.  This is in radians.  If null, then the user tapped outside the globe.
         * @param screenLoc The location on the OpenGL surface.
         */
		void userDidLongPress(GlobeController globeControl, SelectedObject[] selObjs, Point2d loc, Point2d screenLoc);

        /**
         * Called when the globe first starts moving.
         *
         * @param globeControl The globe controller this is associated with.
         * @param userMotion Set if the motion was caused by a gesture.
         */
		void globeDidStartMoving(GlobeController globeControl, boolean userMotion);

        /**
         * Called when the globe stops moving.
         *
         * @param globeControl The globe controller this is associated with.
         * @param corners Corners of the viewport.  If one of them is null, that means it doesn't land on the globe.
         * @param userMotion Set if the motion was caused by a gesture.
         */
		void globeDidStopMoving(GlobeController globeControl, Point3d[] corners, boolean userMotion);

		/**
		 * Called for every single visible frame of movement.  Be careful what you do in here.
		 *
		 * @param globeControl The globe controller this is associated with.
		 * @param corners Corners of the viewport.  If one of them is null, that means it doesn't land on the globe.
		 * @param userMotion Set if the motion was caused by a gesture.
         */
		void globeDidMove(GlobeController globeControl, Point3d[] corners, boolean userMotion);
	}

	/**
	 * Set the gesture delegate to get callbacks when the user taps somewhere.
	 */
	public GestureDelegate gestureDelegate = null;
	
	// Called by the gesture handler to let us know the user tapped
	public void processTap(final Point2d screenLoc)
	{
		if (!isCompletelySetup()) {
			return;
		}

		final GestureDelegate delegate = running ? gestureDelegate : null;
		if (gestureDelegate == null) {
			return;
		}

		final Matrix4d globeTransform = globeView.calcModelViewMatrix();
		final Point3d loc = globeView.pointOnSphereFromScreen(screenLoc, globeTransform, getViewSize(), false);
		if (loc == null) {
			gestureDelegate.userDidTapOutside(this, screenLoc);
			return;
		}

		final Point3d localPt = globeView.getCoordAdapter().displayToLocal(loc);
		Point3d geoPt = null;
		if (localPt != null) {
			geoPt = globeView.getCoordAdapter().getCoordSystem().localToGeographic(localPt);
		}

		final SelectedObject[] selObjs = this.getObjectsAtScreenLoc(screenLoc, vectorSelectDistance);

		if (geoPt != null) {
			if (selObjs != null) {
				gestureDelegate.userDidSelect(this, selObjs, geoPt.toPoint2d(), screenLoc);
			} else {
				// Just a simple tap, then
				gestureDelegate.userDidTap(this, geoPt.toPoint2d(), screenLoc);
			}
		}
	}


	/**
	 * Set the gesture delegate to fire callbacks when the user did long press somewhere
	 * @param screenLoc Screen point of the press
     */
    public void processLongPress(final Point2d screenLoc) {
		if (!isCompletelySetup()) {
			return;
		}

		final GestureDelegate delegate = running ? gestureDelegate : null;
		if (delegate == null) {
			return;
		}

		final Matrix4d globeTransform = globeView.calcModelViewMatrix();
		final Point3d loc = globeView.pointOnSphereFromScreen(screenLoc, globeTransform, renderControl.frameSize, false);
		if (loc == null) {
			return;
		}
		final Point3d localPt = globeView.getCoordAdapter().displayToLocal(loc);
		final Point3d geoPt = (localPt != null) ? globeView.getCoordAdapter().getCoordSystem().localToGeographic(localPt) : null;

		final SelectedObject[] selObjs = this.getObjectsAtScreenLoc(screenLoc, vectorSelectDistance);
		final Point2d pt2d = (geoPt != null) ? geoPt.toPoint2d() : null;
		gestureDelegate.userDidLongPress(this, selObjs, pt2d, screenLoc);
	}

	// Pass the touches on to the gesture handler
	@Override
	public boolean onTouch(View view, MotionEvent e) {
    	view.performClick();
		final GlobeGestureHandler handler = running ? gestureHandler : null;
		return (handler != null) && handler.onTouch(view, e);
	}

    boolean isTilting = false, isPanning = false, isZooming = false, isRotating = false,
			isAnimating = false, isUserMotion = false, isFinalMotion = false;

    public void tiltDidStart(boolean userMotion) { handleStartMoving(userMotion); isTilting = true; }
    public void tiltDidEnd(boolean userMotion) { isTilting = false; handleStopMoving(userMotion); }
    public void panDidStart(boolean userMotion) { handleStartMoving(userMotion); isPanning = true; }
    public void panDidEnd(boolean userMotion) { isPanning = false; handleStopMoving(userMotion); }
    public void zoomDidStart(boolean userMotion) { handleStartMoving(userMotion); isZooming = true; }
    public void zoomDidEnd(boolean userMotion) { isZooming = false; handleStopMoving(userMotion); }
    public void rotateDidStart(boolean userMotion) { handleStartMoving(userMotion); isRotating = true; }
    public void rotateDidEnd(boolean userMotion) { isRotating = false; handleStopMoving(userMotion); }

    /**
     * Called by the gesture handler to filter out start motion events.
     *
     * @param userMotion Set if kicked off by user motion.
     */
    public void handleStartMoving(boolean userMotion)
    {
		if (!isCompletelySetup())
			return;

		final GestureDelegate delegate = gestureDelegate;
		if (delegate != null) {
			if (!userMotion && (isPanning || isRotating || isZooming || isAnimating || isTilting)) {
				// Transitioning from user motion to animation, e.g., for a fling
				final Point3d[] corners = getVisibleCorners();
				delegate.globeDidStopMoving(this, corners, isUserMotion);
				delegate.globeDidStartMoving(this, false);
			} else if (!isPanning && !isRotating && !isZooming && !isAnimating && !isTilting) {
				delegate.globeDidStartMoving(this, userMotion);

				// call doFrame on the next frame
				Choreographer c = Choreographer.getInstance();
				if (c != null) {
					c.removeFrameCallback(this);
					c.postFrameCallback(this);
				}
			}
		}

		isUserMotion = userMotion;
		isAnimating = !userMotion;
	}

	/**
	 * Called by the gesture handler to filter out end motion events.
	 *
	 * @param userMotion Set if kicked off by user motion.
	 */
	public void handleStopMoving(boolean userMotion)
	{
		if (!isCompletelySetup())
			return;

		if (!isPanning && !isRotating && !isZooming && !isTilting) {
			// Call didStopMoving after the next frame update
			isFinalMotion = true;
		}

		isAnimating = false;
	}

	double lastViewUpdate = 0.0;
	/**
	 * Frame callback for the Choreographer
     */
	public void doFrame(long frameTimeNanos)
	{
		if (globeView != null) {
			double newUpdateTime = globeView.getLastUpdatedTime();
			Point3d[] corners = null;
			if (gestureDelegate != null && lastViewUpdate < newUpdateTime) {
				corners = getVisibleCorners();
				gestureDelegate.globeDidMove(this, corners, isUserMotion);
				lastViewUpdate = newUpdateTime;
			}
			if (isFinalMotion) {
				if (gestureDelegate != null) {
					if (corners == null) {
						corners = getVisibleCorners();
					}
					gestureDelegate.globeDidStopMoving(this, corners, isUserMotion);
				}
				isFinalMotion = false;
			}
		}

		Choreographer c = Choreographer.getInstance();
		if (c != null) {
			c.removeFrameCallback(this);
			c.postFrameCallback(this);
		}
	}


    /**
	 * Calculate visible corners for what's currently being seen.
	 * If the eye point is too high, expect null corners.
	 * @return Array of points representing the corners, in clockwise order, or null on error.
     */
    public Point3d[] getVisibleCorners()
    {
		if (!isCompletelySetup())
			return null;

		final Point2d frameSize = renderControl.frameSize;
		final Point2d[] screenCorners = new Point2d[]{
			new Point2d(0.0, 0.0),
			new Point2d(frameSize.getX(), 0.0),
			new Point2d(frameSize.getX(), frameSize.getY()),
			new Point2d(0.0, frameSize.getY()),
        };

        final Matrix4d modelMat = globeView.calcModelViewMatrix();
		final Point3d[] retCorners = new Point3d[4];
		final CoordSystemDisplayAdapter coordAdapter = globeView.getCoordAdapter();
		final CoordSystem coordSys = (coordAdapter != null) ? coordAdapter.getCoordSystem() : null;
        if (coordSys == null || renderWrapper == null ||
				renderWrapper.maplyRender == null || renderControl.frameSize == null) {
			return retCorners;
		}

        for (int ii=0;ii<4;ii++)
        {
            final Point3d globePt = globeView.pointOnSphereFromScreen(screenCorners[ii],modelMat,frameSize,false);
            if (globePt != null) {
				retCorners[ii] = coordSys.localToGeographic(coordAdapter.displayToLocal(globePt));
			}
        }

        return retCorners;
    }
}
