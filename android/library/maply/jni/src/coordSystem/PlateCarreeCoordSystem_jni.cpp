/*  PlateCarreeCoordSystem_jni.cpp
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

#import <jni.h>
#import "CoordSystem_jni.h"
#import "com_mousebird_maply_PlateCarreeCoordSystem.h"

using namespace WhirlyKit;

extern "C"
JNIEXPORT void JNICALL Java_com_mousebird_maply_PlateCarreeCoordSystem_nativeInit
  (JNIEnv *env, jclass cls)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_mousebird_maply_PlateCarreeCoordSystem_initialise
  (JNIEnv *env, jobject obj)
{
	try
	{
		CoordSystemRefClassInfo::getClassInfo()->setHandle(env,obj,
			new CoordSystemRef(std::make_shared<PlateCarreeCoordSystem>()));
	}
	MAPLY_STD_JNI_CATCH()
}
