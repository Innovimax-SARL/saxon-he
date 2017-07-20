#include "SaxonCXPath.h"




    /**
     * Create boxed Boolean value
     * @param val - boolean value
     */
jobject booleanValue(sxnc_environment environ, bool b){ 
	
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	 jclass  booleanClass = lookForClass(environ.env, "java/lang/Boolean");
	 static jmethodID MID_init = NULL;
	 if(!MID_init) {
  	   MID_init = (jmethodID)findConstructor (environ.env, booleanClass, "(Z)V");
	 }
	 jobject booleanValue = (jobject)(*(environ.env))->NewObject(environ.env, booleanClass, MID_init, (jboolean)b);
      	 if (!booleanValue) {
	    	printf("Error: failed to allocate Boolean object\n");
		fflush (stdout);
        	return NULL;
      	 }
	 return booleanValue;
}

    /**
     * Create an boxed Integer value
     * @param val - int value
     */
jobject integerValue(sxnc_environment environ, int i){ 
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }

	 jclass  integerClass = lookForClass(environ.env, "java/lang/Integer");
 	/*static */ jmethodID intMID = NULL;
	 //if(!intMID){
		intMID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, integerClass, "<init>", "(I)V");
	 //}
if(!intMID){
	printf("error in intMID");
}	
	 jobject intValue = (*(environ.env))->NewObject(environ.env, integerClass, intMID, (jint)i);
      	 if (!intValue) {
	    	printf("Error: failed to allocate Integer object\n");
printf("Value to build: %i",i);
		fflush (stdout);
//(*(environ.env))->ExceptionDescribe(environ.env); //remove line
        	return NULL;
      	 }
	 return intValue;
	}


    /**
     * Create an boxed Double value
     * @param val - double value
     */
jobject doubleValue(sxnc_environment environ, double d){ 
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	 jclass  doubleClass = lookForClass(environ.env, "net/sf/saxon/s9api/XdmAtomicValue");
	 static jmethodID dbID = NULL;
	if(!dbID) {
		dbID = (jmethodID)findConstructor (environ.env, doubleClass, "(D)V");
	}	
	 jobject doubleValue = (jobject)(*(environ.env))->NewObject(environ.env, doubleClass, dbID, (jdouble)d);
      	 if (!doubleValue) {
	    	printf("Error: failed to allocate Double object\n");
		fflush (stdout);
        	return NULL;
      	 }
	 return doubleValue;
	}

    /**
     * Create an boxed Float value
     * @param val - float value
     */
jobject floatValue(sxnc_environment environ, float f){ 
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	 jclass  floatClass = lookForClass(environ.env, "java/lang/Float");
	 static jmethodID fID = NULL;
	 if(!fID){
	 	fID = (jmethodID)findConstructor (environ.env, floatClass, "(F)V");
	 }
	 jobject floatValue = (jobject)(*(environ.env))->NewObject(environ.env, floatClass, fID, (jfloat)f);
      	 if (!floatValue) {
	    	printf("Error: failed to allocate float object\n");
		fflush (stdout);
        	return NULL;
      	 }
	 return floatValue;
	}


    /**
     * Create an boxed Long value
     * @param val - Long value
     */
jobject longValue(sxnc_environment environ, long l){ 
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	 jclass  longClass = lookForClass(environ.env, "java/lang/Long");
	 static jmethodID lID = NULL;
	 if(lID) {
		  lID = (jmethodID)findConstructor (environ.env, longClass, "(L)V");
	}	
	 jobject longValue = (jobject)(*(environ.env))->NewObject(environ.env, longClass, lID, (jlong)l);
      	 if (!longValue) {
	    	printf("Error: failed to allocate long object\n");
		fflush (stdout);
        	return NULL;
      	 }
	 return longValue;
	}

    /**
     * Create an boxed String value
     * @param val - as char array value
     */
jobject getJavaStringValue(sxnc_environment environ, const char *str){ 
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	if(str == NULL) {
		return (*(environ.env))->NewStringUTF(environ.env, "");	
	}
	jstring jstrBuf = (*(environ.env))->NewStringUTF(environ.env, str);
	 
      	 if (!jstrBuf) {
	    	printf("Error: failed to allocate String object\n");
		fflush (stdout);
        	return NULL;
      	 }
	 return jstrBuf;
	}



jobject xdmValueAsObj(sxnc_environment environ, const char* type, const char* str){ 
	jclass  saxoncClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/SaxonCAPI");
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	char methodName[] = "createXdmAtomicItem";
    	char args[] = "(Ljava/lang/String;Ljava/lang/String;)Lnet/sf/saxon/s9api/XdmValue;";
		
	static jmethodID MID_xdmValueo = NULL;
	if(!MID_xdmValueo) {
		MID_xdmValueo = (jmethodID)(*(environ.env))->GetStaticMethodID(environ.env, saxoncClass, methodName, args);
	}
       if (!MID_xdmValueo) {
	  printf("\nError: MyClassInDll %s() not found\n",methodName);
  	  fflush (stdout);
          return NULL;
      }
   jobject resultObj = ((*(environ.env))->CallStaticObjectMethod(environ.env, saxoncClass, MID_xdmValueo, type, str));
   if(resultObj) {
	return resultObj;
   } 
   return NULL;
}

    /**
     * A Constructor. Create a XdmValue based on the target type. Conversion is applied if possible
     * @param type - specify target type of the value as the local name of the xsd built in type. For example 'gYearMonth' 
     * @param val - Value to convert
     */
sxnc_value * xdmValue(sxnc_environment environ, const char* type, const char* str){ 
	jclass  saxoncClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/SaxonCAPI");
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	char methodName[] = "createXdmAtomicValue";
    	char args[] = "(Ljava/lang/String;Ljava/lang/String;)Lnet/sf/saxon/s9api/XdmValue;";
		
	static jmethodID MID_xdmValue = NULL;
	if(!MID_xdmValue) {
		MID_xdmValue = (jmethodID)(*(environ.env))->GetStaticMethodID(environ.env, saxoncClass, methodName, args);
	}
       if (!MID_xdmValue) {
	  printf("\nError: MyClassInDll %s() not found\n",methodName);
  	  fflush (stdout);
          return NULL;
      }
   jobject resultObj = ((*(environ.env))->CallStaticObjectMethod(environ.env, saxoncClass, MID_xdmValue, type, str));
   if(resultObj) {
	sxnc_value* result = (sxnc_value *)malloc(sizeof(sxnc_value));
         result->xdmvalue = resultObj; 
	 return result; 
   } 
   return NULL;
}

sxnc_value * evaluate(sxnc_environment environ, sxnc_processor ** proc, char * cwd, char * xpathStr, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen){
static jmethodID emID = NULL; //cache the methodID
	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XPathProcessor");

	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}

	if(emID == NULL) {
		emID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"evaluateSingle", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Lnet/sf/saxon/s9api/XdmItem;");
		if (!emID) {
		        printf("Error: MyClassInDll. evaluateSingle not found\n");
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
	   if((!objectArray) || (!stringArray)) { return 0;}
	   int i=0;
	   for(i =0; i< parLen; i++) {
		
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, parameters[i].name) );
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)(parameters[i].value) );
	
	   }

	   (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env,"resources"));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, getResourceDirectory())) );
	    i++;
	   int j=0;	
  	   for(; j < propLen; j++, i++) {
	     (*(environ.env))->SetObjectArrayElement(environ.env, stringArray, i, (*(environ.env))->NewStringUTF(environ.env, properties[j].name));
	     (*(environ.env))->SetObjectArrayElement(environ.env, objectArray, i, (jobject)((*(environ.env))->NewStringUTF(environ.env, properties[j].value)) );
	   }
	  
	}
      jobject resultObj = (jstring)((*(environ.env))->CallObjectMethod(environ.env, cpp, emID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)), (*(environ.env))->NewStringUTF(environ.env,xpathStr), stringArray, objectArray ));

      (*(environ.env))->DeleteLocalRef(environ.env, objectArray);
      (*(environ.env))->DeleteLocalRef(environ.env, stringArray);
      if(resultObj) {
	 sxnc_value* result = (sxnc_value *)malloc(sizeof(sxnc_value));
         result->xdmvalue = resultObj;  
	
	//checkForException(environ, cppClass, cpp);     
	return result;
     }

    checkForException(environ, cppClass, cpp);
    return 0;

}


bool effectiveBooleanValue(sxnc_environment environ, sxnc_processor ** proc, char * cwd, char * xpathStr, sxnc_parameter *parameters, sxnc_property * properties, int parLen, int propLen){
	static jmethodID bmID = NULL; //cache the methodID
	jclass cppClass = lookForClass(environ.env, "net/sf/saxon/option/cpp/XPathProcessor");

	if(!cpp) {
		cpp = (jobject) createSaxonProcessor (environ.env, cppClass, "(Z)V", NULL, (jboolean)license);
	}

	if(bmID == NULL) {
		bmID = (jmethodID)(*(environ.env))->GetMethodID (environ.env, cppClass,"effectiveBooleanValue", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Z");
		if (!bmID) {
		        printf("Error: MyClassInDll. effectiveBooleanValue not found\n");
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
	   if((!objectArray) || (!stringArray)) { 
		printf("Error: parameter and property arrays have some inconsistencies \n");
		fflush (stdout);		
		return false;}
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
      jboolean resultObj = (jboolean)((*(environ.env))->CallBooleanMethod(environ.env, cpp, bmID, (cwd== NULL ? (*(environ.env))->NewStringUTF(environ.env, "") : (*(environ.env))->NewStringUTF(environ.env, cwd)), (*(environ.env))->NewStringUTF(environ.env,xpathStr), (jobjectArray)stringArray, (jobjectArray)objectArray ));

      (*(environ.env))->DeleteLocalRef(environ.env, objectArray);
      (*(environ.env))->DeleteLocalRef(environ.env, stringArray);
      if(resultObj) {
	 checkForException(environ, cppClass, cpp);    
	return resultObj;
     }

    checkForException(environ, cppClass, cpp);
    return false;
}

const char * getStringValue(sxnc_environment environ, sxnc_value value){
	return stringValue(environ, value.xdmvalue);
}

int size(sxnc_environment environ, sxnc_value val){
	//TODO write method up
	return 0;
}

sxnc_value * itemAt(sxnc_environment environ, sxnc_value val, int i){
jclass  xdmValueClass = lookForClass(environ.env, "net/sf/saxon/s9api/XdmValue");
	 if(environ.env == NULL) {
		printf("Error: Saxon-C env variable is null\n");
		fflush (stdout);
           	return NULL;
	 }
	char methodName[] = "itemAt";
    	char args[] = "(I)Lnet/sf/saxon/s9api/XdmItem;";
		
	static jmethodID MID_xdmValue = NULL;
	if(!MID_xdmValue) {
		MID_xdmValue = (jmethodID)(*(environ.env))->GetMethodID(environ.env, xdmValueClass, methodName, args);
	}
       if (!MID_xdmValue) {
	  printf("\nError: MyClassInDll %s() not found\n",methodName);
  	  fflush (stdout);
          return NULL;
      }
      jobject xdmItemObj = (*(environ.env))->CallObjectMethod(environ.env,val.xdmvalue, MID_xdmValue, i);
      if(xdmItemObj) {   
	 sxnc_value* result = (sxnc_value *)malloc(sizeof(sxnc_value));
         result->xdmvalue = xdmItemObj;  
	
	//checkForException(environ, cppClass, cpp);     
	return result;
     }

    checkForException(environ, xdmValueClass, val.xdmvalue);
    return NULL;
}

bool isAtomicvalue(sxnc_value value){
//TODO
	return false;
}

int getIntegerValue(sxnc_environment environ, sxnc_value value,  int failureVal){
	const char * valueStr = getStringValue(environ, value);
	if(valueStr != NULL) {		
		int value = atoi(valueStr);
		if(value != 0) {
			return value;
		}
	} 

	if (strcmp(valueStr,"0") == 0) {
		return 0;
	} else {
		return failureVal;	
	}}

bool getBooleanValue(sxnc_environment environ, sxnc_value value){return false;}

long getLongValue(sxnc_environment environ, sxnc_value value,  long failureVal){
	const char * valueStr = getStringValue(environ, value);
	if(valueStr != NULL) {		
		long value = atol(valueStr);
		if(value != 0) {
			return value;
		}
	} 

	if (strcmp(valueStr,"0") == 0) {
		return 0;
	} else {
		return failureVal;	
	}
}

float getFloatValue(sxnc_environment environ, sxnc_value value,  float failureVal){return 0;}

double getDoubleValue(sxnc_environment environ, sxnc_value value, double failureVal){
	const char * valueStr = getStringValue(environ, value);
	if(valueStr != NULL) {		
		double value = atof(valueStr);
		if(value != 0) {
			return value;
		}
	} 

	if (strcmp(valueStr,"0") == 0) {
		return 0;
	} else {
		return failureVal;	
	}

}

