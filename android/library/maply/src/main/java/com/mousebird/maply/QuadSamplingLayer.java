/*  QuadSamplingLayer.cpp
 *  WhirlyGlobeLib
 *
 *  Created by Steve Gifford on 3/28/19.
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

import android.util.Log;

import androidx.annotation.CallSuper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * The Quad Sampling Layer runs a quad tree which determines what
 *  tiles to load.  We hook up other things to this to actually do
 *  the loading.
 */
public class QuadSamplingLayer extends Layer implements LayerThread.ViewWatcherInterface
{
    WeakReference<BaseController> control;
    SamplingParams params;

    protected QuadSamplingLayer() { }

    QuadSamplingLayer(BaseController inControl,SamplingParams inParams) {
        control = new WeakReference<BaseController>(inControl);
        params = inParams;

        initialise(params);
    }

    // Used to hook
    public interface ClientInterface {
        void samplingLayerConnect(QuadSamplingLayer layer,ChangeSet changes);
        void samplingLayerDisconnect(QuadSamplingLayer layer,ChangeSet changes);
    }

    /**
     * Add a client to this sampling layer.
     * It'll do its hooking up on the C++ side.
     */
    public void addClient(ClientInterface user)
    {
        ChangeSet changes = new ChangeSet();
        user.samplingLayerConnect(this, changes);
        clients.add(user);
        layerThread.addChanges(changes);
    }

    private final ArrayList<ClientInterface> clients = new ArrayList<>();

    /**
     * Client will stop getting updates from this sampling layer.
     */
    public void removeClient(ClientInterface user)
    {
        if (!clients.contains(user))
            return;
        ChangeSet changes = new ChangeSet();
        user.samplingLayerDisconnect(this, changes);
        clients.remove(user);
        layerThread.addChanges(changes);
    }

    /** --- Layer methods --- */

    public void startLayer(LayerThread inLayerThread)
    {
        super.startLayer(inLayerThread);

        layerThread.addWatcher(this);

        startNative(params,control.get().scene,control.get().renderControl);
    }

    public void preSceneFlush(LayerThread layerThread)
    {
        ChangeSet changes = new ChangeSet();
        preSceneFlushNative(changes);
        layerThread.addChanges(changes);
    }

    // Used for debugging
//    public long ident = Identifiable.genID();


    /**
     * This method is called when the layer will soon be shut down,
     * but outside any lock context and from another thread.
     * This should not block or do any real work.
     */
    @Override
    @CallSuper
    public void preShutdown() {
        super.preShutdown();
        preShutdownNative();
    }

    @Override
    @CallSuper
    public void shutdown()
    {
        ChangeSet changes = new ChangeSet();
        shutdownNative(changes);

        layerThread.addChanges(changes);

        super.shutdown();
    }

    // Used to sunset delayed view updates
    private int generation = 0;

    /** --- View Updated Methods --- **/
    public void viewUpdated(final ViewState viewState)
    {
        if (isShuttingDown || layerThread.isShuttingDown)
            return;

        final int thisGeneration = ++generation;

//        Log.d("Maply", "QuadSamplingLayer: pre-viewUpdatedNative + " + ident);
        try (LayerThread.WorkWrapper wr = layerThread.startOfWorkWrapper()) {
            if (wr == null) {
                return;
            }

            ChangeSet changes = new ChangeSet();
            if (viewUpdatedNative(viewState, changes)) {
                // Have a few things left to process.  So come back in a bit and do them.
                layerThread.addDelayedTask(() -> {
                    // If we've moved on, then skip it
                    if (thisGeneration >= generation) {
                        viewUpdated(viewState);
                    }
                }, LayerThread.UpdatePeriod);
            }
            layerThread.addChanges(changes);
        }
    }

    // Called no more often than 1/10 of a second
    public float getMinTime()
    {
        return 0.1f;
    }

    // Lags no more than 4s (if a user is continuously moving around, basically)
    public float getMaxLagTime()
    {
        return 4.0f;
    }

    // Number of clients attached to this sampling layer
    native int getNumClients();

    private native boolean viewUpdatedNative(ViewState viewState,ChangeSet changes);
    private native void startNative(SamplingParams params,Scene scene,RenderController render);
    private native void preSceneFlushNative(ChangeSet changes);
    private native void preShutdownNative();
    private native void shutdownNative(ChangeSet changes);

    public void finalize() {
        dispose();
    }
    static {
        nativeInit();
    }
    private static native void nativeInit();
    native void initialise(SamplingParams params);
    native void dispose();
    private long nativeHandle;
}
