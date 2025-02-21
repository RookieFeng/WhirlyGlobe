/*  MaplyVectorMarkerStyle.h
 *  WhirlyGlobe-MaplyComponent
 *
 *  Created by Steve Gifford on 1/3/14.
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

#import "vector_styles/MaplyVectorTileMarkerStyle.h"
#import "helpers/MaplyIconManager.h"
#import "vector_tiles/MapboxVectorTiles.h"
#import "NSDictionary+Stuff.h"

@interface MaplyVectorTileSubStyleMarker : NSObject
{
@public
    NSMutableDictionary *desc;
    UIImage *markerImage;
    UIColor *fillColor;
    UIColor *strokeColor;
    float width;
    float height;
    float strokeWidth;
    bool allowOverlap;
    int clusterGroup;
    NSString *markerImageTemplate;
}

@end

@implementation MaplyVectorTileSubStyleMarker
@end

// Marker placement style
@implementation MaplyVectorTileStyleMarker
{
    MaplyVectorStyleSettings *settings;
    NSMutableArray *subStyles;
}

- (instancetype)initWithStyleEntry:(NSDictionary *)styles settings:(MaplyVectorStyleSettings *)inSettings viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
{
    self = [super initWithStyleEntry:styles viewC:viewC];
    settings = inSettings;
    
    NSArray *stylesArray = styles[@"substyles"];
    subStyles = [NSMutableArray array];
    for (NSDictionary *styleEntry in stylesArray)
    {
        MaplyVectorTileSubStyleMarker *subStyle = [[MaplyVectorTileSubStyleMarker alloc] init];
        subStyle->clusterGroup = -1;
        if (styleEntry[@"fill"])
            subStyle->fillColor = [MaplyVectorTileStyle ParseColor:styleEntry[@"fill"]];
        subStyle->strokeColor = nil;
        if (styleEntry[@"stroke"])
            subStyle->strokeColor = [MaplyVectorTileStyle ParseColor:styleEntry[@"stroke"]];
        subStyle->width = inSettings.markerSize;
        if (styleEntry[@"width"])
            subStyle->width = [styleEntry[@"width"] floatValue];
        subStyle->height = subStyle->width;
        if (styleEntry[@"height"])
            subStyle->height = [styleEntry[@"height"] floatValue];
        subStyle->allowOverlap = false;
        if (styleEntry[@"allow-overlap"])
            subStyle->allowOverlap = [styleEntry[@"allow-overlap"] boolValue];
        if (styleEntry[@"cluster"])
            subStyle->clusterGroup = [styleEntry[@"cluster"] intValue];
        subStyle->strokeWidth = 1.0;
        NSString *fileName = nil;
        if (styleEntry[@"file"])
            fileName = styleEntry[@"file"];
        if (subStyle->fillColor && !subStyle->strokeColor)
            subStyle->strokeColor = [UIColor blackColor];
        UIImage *image;
        if (styleEntry[@"image"])
            image = styleEntry[@"image"];
        
        subStyle->desc = [NSMutableDictionary dictionary];
        subStyle->desc[kMaplyEnable] = @NO;
        [self resolveVisibility:styleEntry settings:settings desc:subStyle->desc];
        if (subStyle->clusterGroup > -1)
            subStyle->desc[kMaplyClusterGroup] = @(subStyle->clusterGroup);
      
        if (image)
            subStyle->markerImage = image;
        else if (!fileName || [fileName rangeOfString:@"["].location == NSNotFound)
        {
            if(fileName && settings.iconDirectory)
            {
                fileName = [settings.iconDirectory stringByAppendingPathComponent:fileName];
            }
          
            if(fileName && fileName.isAbsolutePath)
            {
                if(!subStyle->fillColor && !subStyle->strokeColor)
                {
                    UIImage *image = [UIImage imageWithContentsOfFile:fileName];
                    if(image)
                    {
                        if(image.size.width == subStyle->width && image.size.width == subStyle->height) {
                            subStyle->markerImage = image;
                        }
                    }
                }
            }
          
            if(!subStyle->markerImage)
            {
              subStyle->markerImage = [MaplySimpleStyleManager iconForName:fileName
                                                               size:CGSizeMake(settings.markerScale*subStyle->width+2,
                                                                               settings.markerScale*subStyle->height+2)
                                                              color:[UIColor blackColor]
                                                        circleColor:subStyle->fillColor
                                                         strokeSize:settings.markerScale*subStyle->strokeWidth
                                                        strokeColor:subStyle->strokeColor];
            }
            if ([subStyle->markerImage isKindOfClass:[NSNull class]])
                subStyle->markerImage = nil;
        } else
            subStyle->markerImageTemplate = fileName;

        [subStyles addObject:subStyle];
    }
    
    return self;
}

- (void)buildObjects:(NSArray *)vecObjs
             forTile:(MaplyVectorTileData *)tileInfo
               viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
                desc:(NSDictionary * _Nullable)extraDesc
{
    [self buildObjects:vecObjs forTile:tileInfo viewC:viewC desc:extraDesc cancelFn:nil];
}

/// Construct objects related to this style based on the input data.
- (void)buildObjects:(NSArray * _Nonnull)vecObjs
             forTile:(MaplyVectorTileData * __nonnull)tileInfo
               viewC:(NSObject<MaplyRenderControllerProtocol> * _Nonnull)viewC
                desc:(NSDictionary * _Nullable)extraDesc
            cancelFn:(bool(^__nullable)(void))cancelFn
{
    const bool isRetina = [UIScreen mainScreen].scale > 1.0;

    // One marker per object
    NSMutableArray *compObjs = [NSMutableArray array];
    for (MaplyVectorTileSubStyleMarker *subStyle in subStyles)
    {
        NSMutableArray *markers = [NSMutableArray array];
        for (MaplyVectorObject *vec in vecObjs)
        {
            MaplyScreenMarker *marker = [[MaplyScreenMarker alloc] init];
            marker.userObject = vec;
            marker.selectable = self.selectable;
            if(subStyle->markerImage)
            {
                marker.image = subStyle->markerImage;
            }
            else
            {
                NSString *markerName = [self formatText:subStyle->markerImageTemplate forObject:vec];
                marker.image = [MaplySimpleStyleManager iconForName:markerName
                                                       size:CGSizeMake(settings.markerScale*subStyle->width+2,
                                                                       settings.markerScale*subStyle->height+2)
                                                      color:[UIColor blackColor]
                                                circleColor:subStyle->fillColor
                                                 strokeSize:settings.markerScale*subStyle->strokeWidth
                                                  strokeColor:subStyle->strokeColor];
                if ([marker.image isKindOfClass:[NSNull class]])
                {
                    marker.image = nil;
                }
            }

            if (marker.image)
            {
                marker.loc = [vec center];
                marker.layoutImportance = (subStyle->allowOverlap) ? MAXFLOAT : settings.markerImportance;
                marker.selectable = true;
                if (marker.image)
                {
                    // The markers will be scaled up on a retina display, so compensate
                    marker.size = isRetina ?
                        CGSizeMake(settings.markerScale*subStyle->width/2.0, settings.markerScale*subStyle->height/2.0) :
                        CGSizeMake(settings.markerScale*subStyle->width, settings.markerScale*subStyle->height);
                }
                else
                {
                    marker.size = CGSizeMake(settings.markerScale*subStyle->width, settings.markerScale*subStyle->height);
                }
                [markers addObject:marker];
            }
        }

        NSDictionary* desc = subStyle->desc ? subStyle->desc : extraDesc;
        if (subStyle->desc && extraDesc)
        {
            [desc dictionaryByMergingWith:extraDesc];
        }
        MaplyComponentObject *compObj = [viewC addScreenMarkers:markers desc:desc mode:MaplyThreadCurrent];
        if (compObj)
        {
            [compObjs addObject:compObj];
        }
    }
    
    [tileInfo addComponentObjects:compObjs];
}

@end
