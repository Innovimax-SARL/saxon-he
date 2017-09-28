////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


#ifndef SAXONCGLUE_H 
#define SAXONCGLUE_H
#include <jni.h>


#if defined __linux__ || defined __APPLE__
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdio.h>

#define HANDLE void*
#define LoadLibrary(x) dlopen(x, RTLD_LAZY)
#define GetProcAddress(x,y) dlsym(x,y)
#else
#include <windows.h>
#endif

#ifdef __cplusplus
#define EXTERN_C extern "C" {
#define EXTERN_C_END }
#else
#define EXTERN_C
#define EXTERN_C_END
#endif

#ifndef __cplusplus
#ifndef _BOOL
typedef unsigned char bool;
static const bool false = 0;
static const bool true = 1;
#else
static const bool false = 0;
static const bool true = 1;
#endif
#endif

#ifndef __cplusplus 
	#if defined(LICENSE)
		#define license true;
	#else
		#define license false
	#endif
#endif


//#define DEBUG


EXTERN_C






 

static char tempDllname[] =
#if defined (__linux__)
        "/libsaxonhec64.so";  
    #elif  defined (__APPLE__) && defined(__MACH__)
        "/libsaxonhec.dylib";
    #else
         "\\libsaxonhec.dll";
    #endif

static char tempResources_dir[] = 
     #ifdef __linux__
        "/saxon-data";
    #elif  defined (__APPLE__) && defined(__MACH__)
        "/saxon-data";
    #else
         "\\saxon-data";
    #endif


static char * dllname;/*[] =
    #ifdef __linux__
        "/usr/lib/libsaxonhec.so";  //rename according to product edition (hec or pec) Also make change in the c file
    #elif  defined (__APPLE__) && defined(__MACH__)
        "/usr/lib/libsaxoneec.dylib";
    #else
         "C:\\Program Files\\Saxonica\\SaxonHEC1.0.1\\libsaxonhec.dll";
    #endif*/

static char *resources_dir;/*[] = 
     #ifdef __linux__
        "/usr/lib/saxon-data";
    #elif  defined (__APPLE__) && defined(__MACH__)
        "/usr/lib/saxon-data";
    #else
         "C:\\Program Files\\Saxonica\\SaxonHEC1.0.1\\saxon-data";
    #endif*/

// Static variable used to track when jvm has been created. Used to prevent creation more than once.
static int jvmCreated =0;


//===============================================================================================//
/*! <code>Environment</code>. This struct captures the jni, JVM and handler to the cross compiled Saxon/C library.
 * <p/>
 */
typedef struct {
		JNIEnv *env;
		HANDLE myDllHandle;
		JavaVM *jvm;
	} sxnc_environment;


//===============================================================================================//

/*! <code>sxnc_parameter</code>. This struct captures details of paramaters used for the transformation as (string, value) pairs.
 * <p/>
 */
typedef struct {
		char* name;
		jobject value;
	} sxnc_parameter;

//===============================================================================================//

/*! <code>sxnc_property</code>. This struct captures details of properties used for the transformation as (string, string) pairs.
 * <p/>
 */
typedef struct {
		char * name;
		char * value;
	} sxnc_property;



extern jobject cpp;



extern const char * failure;


/*
* Get Dll name.
*/

char * getDllname();


/*
* Get Dll name.
*/

char * getResourceDirectory();

/*
* Set Dll name. Also set the saxon resources directory. 
* If the SAXON_HOME environmental variable is set then use that as base.
*/
void setDllname();

/*
 * Load dll using the default setting in Saxon/C
 * Recommended method to use to load library
 */
HANDLE loadDefaultDll();


/*
 * Load dll.
 * name - The dll library
 */
HANDLE loadDll(char* name);


extern jint (JNICALL * JNI_GetDefaultJavaVMInitArgs_func) (void *args);

extern jint (JNICALL * JNI_CreateJavaVM_func) (JavaVM **pvm, void **penv, void *args);

/*
 * Initialize JET run-time with simplified method. The initJavaRT method will be called 
 * with the arguments expanded from environ
 * @param environ - the Evironment is passed
 */
void initDefaultJavaRT(sxnc_environment ** environ);


/*
 * Initialize JET run-time.
 */
void initJavaRT(HANDLE myDllHandle, JavaVM** pjvm, JNIEnv** penv);


/*
 * Look for class.
 */
jclass lookForClass (JNIEnv* penv, const char* name);


/*
 * Create an object and invoke the instance method
 */
void invokeInstanceMethod (JNIEnv* penv, jclass myClassInDll, char * name, char * arguments);




/*
 * Invoke the static method
 */
void invokeStaticMethod(JNIEnv* penv, jclass myClassInDll, char* name, char* arguments);


/*
 * Find a constructor with a set arguments
 */
jmethodID findConstructor (JNIEnv* penv, jclass myClassInDll, char* arguments);

/*
 * Create the Java SaxonProcessor
 * This can be used to pass the sub-classes of SaxonAPI, there the argument1  should can be null or Processor object
 */
jobject createSaxonProcessor (JNIEnv* penv, jclass myClassInDll, const char * arguments, jobject argument1, jboolean licensei);

/*
 * Create the Java SaxonProcessor
 * This can be used to pass the sub-classes of SaxonAPI, there the argument1  should can be null or Processor object
 */
jobject createSaxonProcessor2 (JNIEnv* penv, jclass myClassInDll, const char * arguments, jobject argument1);

/*
 * Callback to check for exceptions. When called it returns the exception as a string 
 */
const char * checkForException(sxnc_environment environ, jclass callingClass,  jobject callingObject);

/*
 * Clean up and destroy Java VM to release memory used. 
 */
void finalizeJavaRT (JavaVM* jvm);


/*
 * Get a parameter from list 
 */
jobject getParameter(sxnc_parameter *parameters,  int parLen, const char* namespacei, const char * name);

/*
 * Get a property from list 
 */
char* getProperty(sxnc_property * properties, int propLen, const char* namespacei, const char * name);


/*
 * set a parameter 
 */
void setParameter(sxnc_parameter **parameters, int *parLen, int *parCap, const char * namespacei, const char * name, jobject value);


/*
 * set a property 
 */
void setProperty(sxnc_property ** properties, int *propLen, int *propCap, const char* name, const char* value);

/*
 * clear parameter 
 */
void clearSettings(sxnc_parameter **parameters, int *parLen, sxnc_property ** properties, int *propLen);



const char * stringValue(sxnc_environment environ, jobject value);

EXTERN_C_END


#endif //SAXONCGLUE_H
