#include "SaxonCProcessor.h"


/*
 * Get the Saxon version 
 */
const char * version(sxnc_environment environ) {


    jmethodID MID_version;
    jclass  versionClass;
     versionClass = lookForClass(environ.env, "net/sf/saxon/Version");
    char methodName[] = "getProductVersion";
    char args[] = "()Ljava/lang/String;";
    MID_version = (jmethodID)(*(environ.env))->GetStaticMethodID(environ.env, versionClass, methodName, args);
    if (!MID_version) {
	printf("\nError: MyClassInDll %s() not found\n",methodName);
	fflush (stdout);
        return NULL;
    }
   jstring jstr = (jstring)((*(environ.env))->CallStaticObjectMethod(environ.env, versionClass, MID_version));
   const char * str = (*(environ.env))->GetStringUTFChars(environ.env, jstr, NULL);
  
    //(*(environ.env))->ReleaseStringUTFChars(environ.env, jstr,str);
    return str;
}


/*
 * Get the Saxon version 
 */
const char * getProductVariantAndVersion(sxnc_environment environ) {


    jmethodID MID_version;
    jclass  versionClass;
     versionClass = lookForClass(environ.env, "net/sf/saxon/Version");
    char methodName[] = "getProductVariantAndVersion";
    char args[] = "()Ljava/lang/String;";
    MID_version = (jmethodID)(*(environ.env))->GetStaticMethodID(environ.env, versionClass, methodName, args);
    if (!MID_version) {
	printf("\nError: MyClassInDll %s() not found\n",methodName);
	fflush (stdout);
        return NULL;
    }
   jstring jstr = (jstring)((*(environ.env))->CallStaticObjectMethod(environ.env, versionClass, MID_version));
   const char * str = (*(environ.env))->GetStringUTFChars(environ.env, jstr, NULL);
  
    (*(environ.env))->ReleaseStringUTFChars(environ.env, jstr,str);
    return str;
}

void initSaxonc(sxnc_environment ** environ, sxnc_processor ** proc, sxnc_parameter **param, sxnc_property ** prop, int cap, int propCap){
    
    *param = (sxnc_parameter *)calloc(cap, sizeof(sxnc_parameter));
    *prop = (sxnc_property *)calloc(propCap, sizeof(sxnc_property));
    *environ =  (sxnc_environment *)malloc(sizeof(sxnc_environment));
    *proc = (sxnc_processor *)malloc(sizeof(sxnc_processor));
}


void freeSaxonc(sxnc_environment ** environ, sxnc_processor ** proc, sxnc_parameter **param, sxnc_property ** prop){
	free(*environ);
	free(*proc);
	free(*param);
	free(*prop);
}

void xsltSaveResultToFile(sxnc_environment environ, sxnc_processor ** proc, char * cwd, char * source, char* stylesheet, char* outputfile, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen) {
	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XsltProcessor");
	static jmethodID xsltFileID = NULL; //cache the methodID
 	
	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}
 	
	if(!xsltFileID) {
	 	xsltFileID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"transformToFile", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)V");
	 	if (!xsltFileID) {
	        	printf("Error: MyClassInDll. transformToFile not found\n");
			fflush (stdout);
			return;
	    	} 
	}
	
 	jobjectArray stringArray = NULL;
	jobjectArray objectArray = NULL;
	int size = parLen + propLen+1;
	
	

	if(size>0) {
           jclass objectClass = lookForClass(environ.env, "java/lang/Object");
	   jclass stringClass = lookForClass(environ.env, "java/lang/String");
	   objectArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, objectClass, 0 );
	   stringArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, stringClass, 0 );
	if(size >0) {

	   if((!objectArray) || (!stringArray)) { 
		printf("Error: parameter and property have some inconsistencies\n");
		fflush (stdout);
		return;}
	   int i=0;
	   for( i =0; i< parLen; i++) {
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,  parameters[i].name) );
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)(parameters[i].value) );
	   }
 	   (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,"resources"));
	   (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, getResourceDirectory())) );
	   i++;
            int j=0;
  	   for(; j< propLen; j++, i++) {
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, properties[j].name));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, properties[j].value)) );
	   }
	   
	}
	}

      (*(environ.env))->CallVoidMethod(environ.env, cpp, xsltFileID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)),(*(environ.env))->NewStringUTF(environ.env, source), (*(environ.env))->NewStringUTF(environ.env, stylesheet), (*(environ.env))->NewStringUTF(environ.env, outputfile), stringArray, objectArray );
	if(size>0) {    
	  (*(environ.env))->DeleteLocalRef(environ.env, objectArray);
	  (*(environ.env))->DeleteLocalRef(environ.env, stringArray);
	}
#ifdef DEBUG
	checkForException(environ, cppClass, cpp);
#endif
  
}

const char * xsltApplyStylesheet(sxnc_environment environ, sxnc_processor ** proc, char * cwd, const char * source, const char* stylesheet, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen) {
	static jmethodID mID = NULL; //cache the methodID

	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XsltProcessor");

	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}
#ifdef DEBUG
        jmethodID debugMID = (*(environ.env))->GetStaticMethodID(environ.env, cppClass, "setDebugMode", "(Z)V");
	(*(environ.env))->CallStaticVoidMethod(environ.env, cppClass, debugMID, (jboolean)true);
#endif
	if(mID == NULL) {
 		mID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"transformToString", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
 		if (!mID) {
 		       printf("Error: MyClassInDll. xsltApplyStylesheet not found\n");
			fflush (stdout);
			return 0;
 		 } 
	}

 	jobjectArray stringArray = NULL;
	jobjectArray objectArray = NULL;
	int size = parLen + propLen+1; //We add one here for the resources-dir property

	if(size >0) {
           jclass objectClass = lookForClass(environ.env, "java/lang/Object");
	   jclass stringClass = lookForClass(environ.env, "java/lang/String");
	   objectArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, objectClass, 0 );
	   stringArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, stringClass, 0 );
	   if((!objectArray) || (!stringArray)) { return NULL;}
	   int i=0;
	   for(i =0; i< parLen; i++) {
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, parameters[i].name) );
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)(parameters[i].value) );
	
	   }

	   (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,"resources"));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, getResourceDirectory())) );
	    i++;
	    int j=0;		
  	   for(; j<propLen; j++, i++) {
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, properties[j].name));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, properties[j].value)) );
	   }
	  
	}

      jstring result = (jstring)((*(environ.env))->CallObjectMethod(environ.env, cpp, mID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)), (*(environ.env))->NewStringUTF(environ.env, source), (*(environ.env))->NewStringUTF(environ.env, stylesheet), stringArray, objectArray ));

	
      if(result) {
        const char * str = (*(environ.env))->GetStringUTFChars(environ.env, result, NULL);    
	return str;
     }

    checkForException(environ, cppClass, cpp);
    return 0;
}


void executeQueryToFile(sxnc_environment environ, sxnc_processor ** proc, char * cwd, char* outputfile, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen){
	static jmethodID queryFileID = NULL; //cache the methodID
	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XQueryEngine");
 	
	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}
 	
	if(queryFileID == NULL) {	
		queryFileID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"executeQueryToFile", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)V");
 		if (!queryFileID) {
        		printf("Error: MyClassInDll. executeQueryToString not found\n");
			fflush (stdout);
			return;
    		} 
	}
	
 	jobjectArray stringArray = NULL;
	jobjectArray objectArray = NULL;
	int size = parLen + propLen+1;
	
	

	if(size>0) {
           jclass objectClass = lookForClass(environ.env, "java/lang/Object");
	   jclass stringClass = lookForClass(environ.env, "java/lang/String");
	   objectArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, objectClass, 0 );
	   stringArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, stringClass, 0 );
	if(size >0) {

	   if((!objectArray) || (!stringArray)) { return;}
	   int i=0;
	   for( i =0; i< parLen; i++) {
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,  parameters[i].name) );
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)(parameters[i].value) );
	   }
 	   (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,"resources"));
	   (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, getResourceDirectory())) );
	   i++;
	  int j=0;
  	   for(; j<propLen; i++, j++) {
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, properties[j].name));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, properties[j].value)) );
	   }
	   
	}
	}
      (*(environ.env))->CallVoidMethod(environ.env,cpp, queryFileID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)), (*(environ.env))->NewStringUTF(environ.env, outputfile), stringArray, objectArray );    
	  (*(environ.env))->DeleteLocalRef(environ.env, objectArray);
	  (*(environ.env))->DeleteLocalRef(environ.env, stringArray);

}

const char * executeQueryToString(sxnc_environment environ, sxnc_processor ** proc, char * cwd, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen){
	static jmethodID queryStrID = NULL; //cache the methodID
	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XQueryEngine");

	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}

	if(queryStrID == NULL) {
		queryStrID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"executeQueryToString", "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
		if (!queryStrID) {
		        printf("Error: MyClassInDll. executeQueryToString not found\n");
			fflush (stdout);
			return 0;
		 } 
	}
 	
	jobjectArray stringArray = NULL;
	jobjectArray objectArray = NULL;
	int size = parLen + propLen+1; //We add one here for the resources-dir property

	if(size >0) {
           jclass objectClass = lookForClass(environ.env, "java/lang/Object");
	   jclass stringClass = lookForClass(environ.env, "java/lang/String");
	   objectArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, objectClass, 0 );
	   stringArray = (*(environ.env))->NewObjectArray(environ.env, (jint)size, stringClass, 0 );
	   if((!objectArray) || (!stringArray)) { return NULL;}
	   int i=0;
	   for(i =0; i< parLen; i++) {
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, parameters[i].name) );
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)(parameters[i].value) );
	
	   }


	   (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,"resources"));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, getResourceDirectory())) );
	    i++;
	    int j=0;	
  	   for(; j<propLen; i++, j++) {
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, properties[j].name));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, properties[j].value)) );
	   }
	  
	}
      jstring result = (jstring)((*(environ.env))->CallObjectMethod(environ.env, cpp, queryStrID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)), stringArray, objectArray ));

      (*(environ.env))->DeleteLocalRef(environ.env, objectArray);
      (*(environ.env))->DeleteLocalRef(environ.env, stringArray);
      if(result) {

       const char * str = (*(environ.env))->GetStringUTFChars(environ.env, result, NULL);
       //return "result should be ok";       
	//checkForException(environ, cppClass, cpp);     
	return str;
     }

    checkForException(environ, cppClass, cpp);
    return 0;

}







