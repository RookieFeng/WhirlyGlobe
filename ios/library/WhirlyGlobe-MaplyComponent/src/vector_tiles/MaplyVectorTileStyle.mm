/*  MaplyVectorTileStyle.mm
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

#import "vector_styles/MaplyVectorStyle.h"
#import "vector_styles/MaplyVectorTileLineStyle.h"
#import "vector_styles/MaplyVectorTileMarkerStyle.h"
#import "vector_styles/MaplyVectorTilePolygonStyle.h"
#import "vector_styles/MaplyVectorTileTextStyle.h"
#import "WhirlyGlobe.h"
#import "control/MaplyBaseViewController.h"

using namespace WhirlyKit;

@implementation MaplyVectorTileStyle

+ (id)styleFromStyleEntry:(NSDictionary *)styleEntry settings:(MaplyVectorStyleSettings *)settings viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
{
    MaplyVectorTileStyle *tileStyle = nil;
    
    NSString *typeStr = styleEntry[@"type"];
    if ([typeStr isEqualToString:@"LineSymbolizer"])
    {
        tileStyle = [[MaplyVectorTileStyleLine alloc] initWithStyleEntry:styleEntry settings:settings viewC:viewC];
    } else if ([typeStr isEqualToString:@"PolygonSymbolizer"] || [typeStr isEqualToString:@"PolygonPatternSymbolizer"])
    {
        tileStyle = [[MaplyVectorTileStylePolygon alloc] initWithStyleEntry:styleEntry settings:settings viewC:viewC];
    } else if ([typeStr isEqualToString:@"TextSymbolizer"])
    {
        tileStyle = [[MaplyVectorTileStyleText alloc] initWithStyleEntry:styleEntry settings:settings viewC:viewC];
    } else if ([typeStr isEqualToString:@"MarkersSymbolizer"])
    {
        tileStyle = [[MaplyVectorTileStyleMarker alloc] initWithStyleEntry:styleEntry settings:settings viewC:viewC];
    } else {
        // Set up one that doesn't do anything
        NSLog(@"Unknown symbolizer type %@",typeStr);
        tileStyle = [[MaplyVectorTileStyle alloc] init];
    }
    
    return tileStyle;
}

// Parse a UIColor from hex values
+ (UIColor *) ParseColor:(NSString *)colorStr
{
    return [MaplyVectorTileStyle ParseColor:colorStr alpha:1.0];
}

+ (UIColor *) ParseColor:(NSString *)colorStr alpha:(CGFloat)alpha
{
    // Hex color string
    if ([colorStr characterAtIndex:0] == '#')
    {
        int red = 255, green = 255, blue = 255;
        // parse the hex
        NSScanner *scanner = [NSScanner scannerWithString:colorStr];
        unsigned int colorVal;
        [scanner setScanLocation:1]; // bypass #
        [scanner scanHexInt:&colorVal];
        blue = colorVal & 0xFF;
        green = (colorVal >> 8) & 0xFF;
        red = (colorVal >> 16) & 0xFF;
        
        return [UIColor colorWithRed:red/255.0*alpha green:green/255.0*alpha blue:blue/255.0*alpha alpha:alpha];
    } else if ([colorStr rangeOfString:@"rgba"].location == 0)
    {
        NSScanner *scanner = [NSScanner scannerWithString:colorStr];
        NSMutableCharacterSet *skipSet = [[NSMutableCharacterSet alloc] init];
        [skipSet addCharactersInString:@"(), "];
        [scanner setCharactersToBeSkipped:skipSet];
        [scanner setScanLocation:5];
        int red,green,blue;
        [scanner scanInt:&red];
        [scanner scanInt:&green];
        [scanner scanInt:&blue];
        float locAlpha;
        [scanner scanFloat:&locAlpha];
        
        return [UIColor colorWithRed:red/255.0*alpha green:green/255.0*alpha blue:blue/255.0*alpha alpha:locAlpha*alpha];
    }
    
    return [UIColor colorWithWhite:1.0 alpha:alpha];
}

- (instancetype)initWithStyleEntry:(NSDictionary *)styleEntry viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
{
    self = [super init];
    _viewC = viewC;
    _uuid = [styleEntry[@"uuid"] longLongValue];
    if ([styleEntry[@"tilegeom"] isEqualToString:@"add"])
        self.geomAdditive = true;
    _selectable = styleEntry[kMaplySelectable];
    
    return self;
}

- (NSString *)getCategory
{
    return nil;
}

- (void)resolveVisibility:(NSDictionary *)styleEntry settings:(MaplyVectorStyleSettings *)settings desc:(NSMutableDictionary *)desc
{
    MaplyBaseViewController *thisViewC = (MaplyBaseViewController *)self.viewC;
    if (![thisViewC isKindOfClass:[MaplyBaseViewController class]])
        return;
    
    float minVis = DrawVisibleInvalid;
    float maxVis = DrawVisibleInvalid;
    if (styleEntry[@"minscaledenom"])
    {
        float minScale = [styleEntry[@"minscaledenom"] floatValue];
        minVis = [thisViewC heightForMapScale:minScale] * settings.mapScaleScale;
    }
    if (styleEntry[@"maxscaledenom"])
    {
        float maxScale = [styleEntry[@"maxscaledenom"] floatValue];
        maxVis = [thisViewC heightForMapScale:maxScale] * settings.mapScaleScale;
    }
    if (minVis != DrawVisibleInvalid)
    {
        desc[kMaplyMinVis] = @(minVis);
        if (maxVis != DrawVisibleInvalid)
            desc[kMaplyMaxVis] = @(maxVis);
        else
            desc[kMaplyMaxVis] = @(20.0);
    } else if (maxVis != DrawVisibleInvalid)
    {
        desc[kMaplyMinVis] = @(0.0);
        desc[kMaplyMaxVis] = @(maxVis);
    }
}

- (void)buildObjects:(NSArray *)vecObjs
             forTile:(MaplyVectorTileData *)tileInfo
               viewC:(NSObject<MaplyRenderControllerProtocol> *)viewC
                desc:(NSDictionary * _Nullable)extraDesc
{
}

/// Construct objects related to this style based on the input data.
- (void)buildObjects:(NSArray * _Nonnull)vecObjs
             forTile:(MaplyVectorTileData * __nonnull)tileInfo
               viewC:(NSObject<MaplyRenderControllerProtocol> * _Nonnull)viewC
                desc:(NSDictionary * _Nullable)extraDesc
            cancelFn:(bool(^__nullable)(void))cancelFn
{
}

//sometimes we get strings that look like [name]+'\n '+[ele]
- (NSString*)formatText:(NSString*)formatString forObject:(MaplyVectorObject*)vec
{
    if (!formatString)
    {
        return nil;
    }
    
    // Note: This is a terrible hack.  Change the regex string or fix the data.
    {
        NSMutableDictionary *attributes = (NSMutableDictionary *)vec.attributes;
        if (attributes[@"NAME"] && !attributes[@"name"])
            attributes[@"name"] = attributes[@"NAME"];
    }

    @try {
        //Do variable substitution on [ ... ]
        NSMutableString *result;
        {
            NSError *error;
            NSRegularExpression *regex = [[NSRegularExpression alloc] initWithPattern:@"\\[[^\\[\\]]+\\]"
                                                                              options:0
                                                                                error:&error];
            NSArray* matches = [regex matchesInString:formatString
                                              options:0
                                                range:NSMakeRange(0, formatString.length)];
            
            if(matches.count)
            {
                NSDictionary *attributes = vec.attributes;
                result = [NSMutableString stringWithString:formatString];
                for (int i=(int)matches.count-1; i>= 0; i--)
                {
                    NSTextCheckingResult* match = matches[i];
                    NSString *matchedStr = [formatString substringWithRange:NSMakeRange(match.range.location + 1,
                                                                                        match.range.length - 2)];
                    id replacement = attributes[matchedStr]?:@"";
                    if([replacement isKindOfClass:[NSNumber class]])
                    {
                        replacement = [replacement stringValue];
                    }
                    [result replaceCharactersInRange:match.range withString:replacement];
                }
            }
        }
        
        //replace \n with a newline
        if([formatString rangeOfString:@"\\"].location != NSNotFound )
        {
            if(!result)
            {
                result = [NSMutableString stringWithString:formatString];
            }
            [result replaceOccurrencesOfString:@"\\n"
                                    withString:@"\n"
                                       options:0
                                         range:NSMakeRange(0, result.length)];
        }
        
        //replace + and surrounding whitespace
        //This should probably check if the plus is surrounded by '', but for now i havent needed that
        {
            NSError *error;
            NSRegularExpression *regex = [[NSRegularExpression alloc] initWithPattern:@"\\s?\\+\\s?"
                                                                              options:0
                                                                                error:&error];
            NSArray* matches = [regex matchesInString:result?:formatString
                                              options:0
                                                range:NSMakeRange(0, result?result.length:formatString.length)];
            
            if(matches.count)
            {
                if(!result)
                {
                    result = [NSMutableString stringWithString:formatString];
                }
                for (int i=(int)matches.count-1; i>= 0; i--)
                {
                    NSTextCheckingResult* match = matches[i];
                    [result deleteCharactersInRange:match.range];
                }
            }
        }
        
        //replace quotes around quoted strings
        {
            NSError *error;
            NSRegularExpression *regex = [[NSRegularExpression alloc] initWithPattern:@"'[^\\.]+'"
                                                                              options:0
                                                                                error:&error];
            NSArray* matches = [regex matchesInString:result?:formatString
                                              options:0
                                                range:NSMakeRange(0, result?result.length:formatString.length)];
            
            if(matches.count)
            {
                if(!result)
                {
                    result = [NSMutableString stringWithString:formatString];
                }
                for (int i=(int)matches.count-1; i>= 0; i--)
                {
                    NSTextCheckingResult* match = matches[i];
                    NSString *matchedStr = [result substringWithRange:NSMakeRange(match.range.location + 1,
                                                                                        match.range.length - 2)];
                    [result replaceCharactersInRange:match.range withString:matchedStr];
                }
            }
        }
        
        if(result)
        {
            return result;
        } else {
            return formatString;
        }
    }
    @catch (NSException *exception){
        NSLog(@"Error formatting string:\"%@\" exception:%@", formatString, exception);
        return nil;
    }
}


- (NSString*)description
{
    return [NSString stringWithFormat:@"%@ uuid:%@ additive:%d",
          [[self class] description], @(self.uuid), self.geomAdditive];
}

@end
