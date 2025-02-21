/*
 *  OverlapHelper.h
 *  WhirlyGlobeLib
 *
 *  Created by Steve Gifford on 9/28/15.
 *  Copyright 2011-2019 mousebird consulting.
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
 *
 */

#import <math.h>
#import <set>
#import <map>
#import "Identifiable.h"
#import "BasicDrawable.h"
#import "Scene.h"
#import "SceneRenderer.h"
#import "ScreenSpaceBuilder.h"
#import "SelectionManager.h"
#import "WhirlyVector.h"


namespace WhirlyKit
{
class LayoutObjectEntry;
class LayoutObject;
using LayoutObjectEntryRef = std::shared_ptr<LayoutObjectEntry>;

// We use this to avoid overlapping labels
class OverlapHelper
{
public:
    EIGEN_MAKE_ALIGNED_OPERATOR_NEW;

    OverlapHelper(const Mbr &mbr,int sizeX,int sizeY);
    
    // Try to add an object.  Might fail (kind of the whole point).
    bool addCheckObject(const Point2dVector &pts);
    
    // See if there's an object in the way
    bool checkObject(const Point2dVector &pts);
    
    // Force an object in no matter what
    void addObject(const Point2dVector &pts);
    
protected:
    void calcCells(const Mbr &objMbr, int &sx, int &sy, int &ex, int &ey);
    bool checkObject(const Point2dVector &pts, const Mbr &objMbr, int sx, int sy, int ex, int ey);

    // Object and its bounds
    class BoundedObject
    {
    public:
        ~BoundedObject() { }
        Point2dVector pts;
    };
    
    Mbr mbr;
    std::vector<BoundedObject> objects;
    int sizeX,sizeY;
    Point2f cellSize;
    std::vector<std::vector<int> > grid;
};

// Used to figure out what clusters
class ClusterHelper
{
public:
    EIGEN_MAKE_ALIGNED_OPERATOR_NEW;

    ClusterHelper(const Mbr &mbr,int sizeX, int sizeY, float resScale, Point2d clusterMarkerSize);
    
    // Add an object, possibly forming a group
    void addObject(LayoutObjectEntryRef objEntry,const Point2dVector &pts);

    // Deal with cluster to cluster overlap
    void resolveClusters(volatile bool &cancel);
    
    // Single object with its bounds
    struct ObjectWithBounds
    {
        Point2dVector pts;
        Point2d center;
    };
    
    // Simple object we're trying to cluster
    class SimpleObject : public ObjectWithBounds
    {
    public:
        SimpleObject();
        std::shared_ptr<LayoutObjectEntry> objEntry;
        int parentObject;
    };
    
    // Object we create when there are overlaps
    struct ClusterObject : public ObjectWithBounds
    {
        std::vector<int> children;
    };
    
    // List of objects for this cluster
    void objectsForCluster(const ClusterObject &cluster,
                           std::vector<LayoutObjectEntryRef> &layoutObjs);

    // Add the given index to the cells it covers
    void addToCells(const Mbr &objMbr, int index);
    
    // Remove the given index from the cells it covers
    void removeFromCells(const Mbr &objMbr, int index);
    
    // Return all the objects within the overlap
    void findObjectsWithin(const Mbr &mbr,std::set<int> &objSet);
    
    void calcCells(const Mbr &mbr,int &sx,int &sy,int &ex,int &ey);

    Point2d clusterMarkerSize;
    
    Mbr mbr;
    std::vector<SimpleObject> simpleObjects;
    std::vector<ClusterObject> clusterObjects;

    // Grid we're sorting into for fast lookup
    int sizeX,sizeY;
    float resScale;
    Point2d cellSize;
    std::vector<std::set<int> > grid;
};
    
}
