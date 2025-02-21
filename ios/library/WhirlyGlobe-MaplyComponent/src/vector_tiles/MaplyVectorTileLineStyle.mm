/*  MaplyVectorLineStyle.mm
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

#import <vector>
#import "vector_styles/MaplyVectorTileLineStyle.h"
#import "visual_objects/MaplyTexture.h"
#import "helpers/MaplyTextureBuilder.h"
#import <WhirlyGlobe_iOS.h>
#import "vector_tiles/MapboxVectorTiles.h"
#import "NSDictionary+Stuff.h"

// Line styles
@implementation MaplyVectorTileStyleLine
{
    NSMutableArray *subStyles;
    std::vector<bool> wideVecs;
}

- (instancetype)initWithStyleEntry:(NSDictionary *)style settings:(MaplyVectorStyleSettings *)settings viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
{
    self = [super initWithStyleEntry:style viewC:viewC];

    subStyles = [NSMutableArray array];
    NSArray *subStylesArray = style[@"substyles"];
    wideVecs.resize([subStylesArray count],false);
    int which = 0;
    for (NSMutableDictionary *styleEntry in subStylesArray)
    {
        float strokeWidth = 1.0;
        float alpha = 1.0;
        UIColor *strokeColor = [UIColor blackColor];
      
        // Build up the vector description dictionary
        if (styleEntry[@"stroke-width"])
            strokeWidth = [styleEntry[@"stroke-width"] floatValue];
        if (styleEntry[@"stroke-opacity"])
        {
            alpha = [styleEntry[@"stroke-opacity"] floatValue];
        } else if(styleEntry[@"opacity"]) {
            alpha = [styleEntry[@"opacity"] floatValue];
        }
        if (styleEntry[@"stroke"])
        {
            strokeColor = [MaplyVectorTileStyle ParseColor:styleEntry[@"stroke"] alpha:alpha];
        }
        
        int drawPriority = 0;
        if (styleEntry[@"drawpriority"])
        {
            drawPriority = (int)[styleEntry[@"drawpriority"] integerValue];
        }
        NSMutableDictionary *desc = [NSMutableDictionary dictionaryWithDictionary:
                                     @{kMaplyVecWidth: @(settings.lineScale * strokeWidth),
                                       kMaplyColor: strokeColor,
                                       kMaplyDrawPriority: @(drawPriority+kMaplyVectorDrawPriorityDefault),
                                       kMaplyEnable: @NO,
                                       kMaplyFade: @0.0,
                                       kMaplyVecCentered: @YES,
                                       kMaplySelectable: @(self.selectable)
                                       }];
        
        // Decide whether to use wide lines in this particular sub style
        bool useWideVectors = (settings.useWideVectors && (strokeWidth >= settings.wideVecCuttoff || styleEntry[@"stroke-dasharray"]));
        wideVecs[which] = useWideVectors;
        
        if(useWideVectors)
        {
            int patternLength = 0;
            float repeatLen = 1.0;
            NSArray *dashComponents;
            if(styleEntry[@"stroke-dasharray"])
            {
                NSArray *componentStrings = [styleEntry[@"stroke-dasharray"] componentsSeparatedByString:@","];
                if (componentStrings.count == 1)
                    componentStrings = [styleEntry[@"stroke-dasharray"] componentsSeparatedByString:@" "];
                NSMutableArray *componentNumbers = [NSMutableArray arrayWithCapacity:componentStrings.count];
                for(const NSString *s in componentStrings) {
                    const int n = [s intValue] * settings.dashPatternScale;
                    patternLength += n;
                    if(n > 0) {
                        [componentNumbers addObject:@(n)];
                    }
                }
                dashComponents = componentNumbers;

                // We seem to need powers of two for some devices.  Not totally clear on why.
                repeatLen = patternLength;
            } else  {
                patternLength = 32;
                repeatLen = patternLength;
                dashComponents = @[@(patternLength)];
            }

            // Apply the scale and truncate to integer.
            // TODO: round instead of truncate?
            int width = (int)(settings.lineScale * strokeWidth);

            // Width needs to be a bit bigger for falloff at edges to work
            // TODO: maybe not needed any more?
            if (width < 1)
                width = 1;

            // For odd sizes, we'll expand by 2, even 1
            if (width & 0x1)
                width += 2;
            else
                width += 1;

            MaplyLinearTextureBuilder *lineTexBuilder = [[MaplyLinearTextureBuilder alloc] init];
            [lineTexBuilder setPattern:dashComponents];
            UIImage *lineImage = [lineTexBuilder makeImage];
            MaplyTexture *filledLineTex = [viewC addTexture:lineImage
                                                       desc:@{kMaplyTexMinFilter: kMaplyMinFilterLinear,
                                                              kMaplyTexMagFilter: kMaplyMinFilterLinear,
                                                              kMaplyTexWrapX: @true,
                                                              kMaplyTexWrapY: @true,
                                                              kMaplyTexFormat: @(MaplyImageIntRGBA)}
                                                       mode:MaplyThreadCurrent];
            desc[kMaplyVecTexture] = filledLineTex;
            desc[kMaplyWideVecCoordType] = kMaplyWideVecCoordTypeScreen;
            desc[kMaplyWideVecTexRepeatLen] = @(repeatLen);
            desc[kMaplyVecWidth] = @(width);
        } else {
            // If we're not using wide vectors, figure out the width
            desc[kMaplyVecWidth] = @(settings.lineScale * strokeWidth * settings.oldVecWidthScale);
        }
                
        desc[kMaplySelectable] = @(settings.selectable);

        [self resolveVisibility:styleEntry settings:settings desc:desc];
        
        [subStyles addObject:desc];
        which++;
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
    MaplyComponentObject *baseWideObj = nil;
    MaplyComponentObject *baseRegObj = nil;
    NSMutableArray *compObjs = [NSMutableArray array];
    int which = 0;
    for (__strong NSDictionary *desc in subStyles)
    {
        if (extraDesc)
        {
            desc = [desc dictionaryByMergingWith:extraDesc];
        }

        MaplyComponentObject *compObj = nil;
        if (wideVecs[which])
        {
            if (!baseWideObj)
            {
                baseWideObj = compObj = [viewC addWideVectors:vecObjs desc:desc mode:MaplyThreadCurrent];
            }
            else
            {
                compObj = [viewC instanceVectors:baseWideObj desc:desc mode:MaplyThreadCurrent];
            }
        }
        else
        {
            if (!baseRegObj)
            {
                baseRegObj = compObj = [viewC addVectors:vecObjs desc:desc mode:MaplyThreadCurrent];
            }
            else
            {
                compObj = [viewC instanceVectors:baseRegObj desc:desc mode:MaplyThreadCurrent];
            }
        }

        if (compObj)
        {
            [compObjs addObject:compObj];
        }
        which++;
    }
    
    [tileInfo addComponentObjects:compObjs];
}

@end
