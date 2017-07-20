// XsltProcessor.cpp : Defines the exported functions for the DLL application.
//

//#include "xsltProcessor.cc"

#include "XsltProcessor.h"
#include "XdmValue.h"
#include "XdmItem.h"
#include "XdmNode.h"
#include "XdmAtomicValue.h"
#ifdef DEBUG
#include <typeinfo> //used for testing only
#endif

XsltProcessor::XsltProcessor() {

	SaxonProcessor *p = new SaxonProcessor(false);
	XsltProcessor(p, "");

}

XsltProcessor::XsltProcessor(SaxonProcessor * p, std::string curr) {

	proc = p;

	/*
	 * Look for class.
	 */
	cppClass = lookForClass(SaxonProcessor::sxn_environ->env,
			"net/sf/saxon/option/cpp/XsltProcessor");
	if ((proc->proc) == NULL) {
		cerr << "Processor is NULL" << endl;
	}

	cppXT = createSaxonProcessor2(SaxonProcessor::sxn_environ->env, cppClass,
			"(Lnet/sf/saxon/s9api/Processor;)V", proc->proc);

#ifdef DEBUG
	jmethodID debugMID = SaxonProcessor::sxn_environ->env->GetStaticMethodID(cppClass, "setDebugMode", "(Z)V");
	SaxonProcessor::sxn_environ->env->CallStaticVoidMethod(cppClass, debugMID, (jboolean)true);
#endif    

	nodeCreated = false;
	proc->exception = NULL;
	outputfile1 = "";
	if(!(proc->cwd.empty()) && curr.empty()){
		cwdXT = proc->cwd;
	} else {
		cwdXT = curr;
	}

}

bool XsltProcessor::exceptionOccurred() {
	return proc->exceptionOccurred();
}

const char * XsltProcessor::getErrorCode(int i) {
 if(proc->exception == NULL) {return NULL;}
 return proc->exception->getErrorCode(i);
 }

void XsltProcessor::setSourceFromXdmValue(XdmItem * value) {
    if(value != NULL){
      value->incrementRefCount();
      parameters["node"] = value;
    }
}

void XsltProcessor::setSourceFromFile(const char * ifile) {
	setProperty("s", ifile);
}

void XsltProcessor::setOutputFile(const char * ofile) {
	outputfile1 = std::string(ofile);
	setProperty("o", ofile);
}

void XsltProcessor::setParameter(const char* name, XdmValue * value) {
	if(value != NULL){
		value->incrementRefCount();
		parameters["param:"+std::string(name)] = value;
	} 
}

XdmValue* XsltProcessor::getParameter(const char* name) {
        std::map<std::string, XdmValue*>::iterator it;
        it = parameters.find("param:"+std::string(name));
        if (it != parameters.end())
          return it->second;
        return NULL;
}

bool XsltProcessor::removeParameter(const char* name) {
	return (bool)(parameters.erase("param:"+string(name)));
}

void XsltProcessor::setProperty(const char* name, const char* value) {
#ifdef DEBUG	
	if(value == NULL) {
		std::cerr<<"XSLTProc setProperty is NULL"<<std::endl;
	}
#endif
	properties.insert(std::pair<std::string, std::string>(std::string(name), std::string((value == NULL ? "" : value))));

}

const char* XsltProcessor::getProperty(const char* name) {
        std::map<std::string, std::string>::iterator it;
        it = properties.find(std::string(name));
        if (it != properties.end())
          return it->second.c_str();
	return NULL;
}

void XsltProcessor::clearParameters(bool delValues) {
	if(delValues){
       		for(std::map<std::string, XdmValue*>::iterator itr = parameters.begin(); itr != parameters.end(); itr++){
			//cout<<"param-name:"<<itr->first<<endl;
			/*string s1 = typeid(itr->second).name();
			cerr<<"Type of itr:"<<s1<<endl;*/
			XdmValue * value = itr->second;
			value->decrementRefCount();
#ifdef DEBUG
			cout<<"clearParameter() - XdmValue refCount="<<value->getRefCount()<<endl;
#endif
			if(value != NULL && value->getRefCount() < 1){		
	        		delete value;
			}
        	}
		
	}
	parameters.clear();
}

void XsltProcessor::clearProperties() {
	properties.clear();
}



std::map<std::string,XdmValue*>& XsltProcessor::getParameters(){
	std::map<std::string,XdmValue*>& ptr = parameters;
	return ptr;
}

std::map<std::string,std::string>& XsltProcessor::getProperties(){
	std::map<std::string,std::string> &ptr = properties;
	return ptr;
}

void XsltProcessor::exceptionClear(){
 if(proc->exception != NULL) {
 	delete proc->exception;
 	proc->exception = NULL;
	SaxonProcessor::sxn_environ->env->ExceptionClear();
 }
  
 }

   void XsltProcessor::setcwd(const char* dir){
    cwdXT = std::string(dir);
   }

const char* XsltProcessor::checkException() {
	/*if(proc->exception == NULL) {
	 proc->exception = proc->checkForException(environ, cppClass, cpp);
	 }
	 return proc->exception;*/
	return checkForException(*(SaxonProcessor::sxn_environ), cppClass, cppXT);
}

int XsltProcessor::exceptionCount(){
 if(proc->exception != NULL){
 return proc->exception->count();
 }
 return 0;
 }

void XsltProcessor::compileFromString(const char* stylesheetStr) {
	static jmethodID cStringmID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"createStylesheetFromString",
					"(Ljava/lang/String;Ljava/lang/String;)Lnet/sf/saxon/s9api/XsltExecutable;");
	if (!cStringmID) {
		cerr << "Error: MyClassInDll." << "createStylesheetFromString"
				<< " not found\n" << endl;

	} else {

		stylesheetObject = (jobject)(
				SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, cStringmID,
						SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
						SaxonProcessor::sxn_environ->env->NewStringUTF(stylesheetStr)));
		if (!stylesheetObject) {
			if(exceptionOccurred()) {
				
				if(proc->exception != NULL) {
					delete proc->exception;
				}
				proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
				proc->exceptionClear();
     			}
		}
	}

}

void XsltProcessor::compileFromXdmNode(XdmNode * node) {
	static jmethodID cNodemID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"createStylesheetFromFile",
					"(Ljava/lang/String;Lnet/sf/saxon/s9api/XdmNode;)Lnet/sf/saxon/s9api/XsltExecutable;");
	if (!cNodemID) {
		cerr << "Error: MyClassInDll." << "createStylesheetFromXdmNode"
				<< " not found\n" << endl;

	} else {
		releaseStylesheet();
		stylesheetObject = (jobject)(
				SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, cNodemID,
						SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
						node->getUnderlyingValue(proc)));
		if (!stylesheetObject) {
			if(exceptionOccurred()) {
				if(proc->exception != NULL) {
					delete proc->exception;
				}
				proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
				proc->exceptionClear();
     			}
			//cout << "Error in compileFromXdmNode" << endl;
		}
	}

}

void XsltProcessor::compileFromFile(const char* stylesheet) {
	static jmethodID cFilemID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"createStylesheetFromFile",
					"(Ljava/lang/String;Ljava/lang/String;)Lnet/sf/saxon/s9api/XsltExecutable;");
	if (!cFilemID) {
		cerr << "Error: MyClassInDll." << "createStylesheetFromFile"
				<< " not found\n" << endl;

	} else {
		releaseStylesheet();

		stylesheetObject = (jobject)(
				SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, cFilemID,
						SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
						SaxonProcessor::sxn_environ->env->NewStringUTF(stylesheet)));
		if (!stylesheetObject) {
			if(exceptionOccurred()) {
				
				if(proc->exception != NULL) {
					delete proc->exception;
				}
				proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
				proc->exceptionClear();
	   		}
     		
		}
		//SaxonProcessor::sxn_environ->env->NewGlobalRef(stylesheetObject);
	}

}

void XsltProcessor::releaseStylesheet() {

	stylesheetObject = NULL;
	
}



XdmValue * XsltProcessor::transformFileToValue(const char* sourcefile,
		const char* stylesheetfile) {

	if(sourcefile == NULL && stylesheetfile == NULL && !stylesheetObject){
		cerr<< "Error: The most recent Stylesheet Object failed. Please check exceptions"<<endl;
		return NULL;
	}

	setProperty("resources", proc->getResourcesDirectory());
	static jmethodID mID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"transformToNode",
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Lnet/sf/saxon/s9api/XdmNode;");
	if (!mID) {
		cerr << "Error: MyClassInDll." << "transformtoNode" << " not found\n"
				<< endl;

	} else {
		jobjectArray stringArray = NULL;
		jobjectArray objectArray = NULL;
		jclass objectClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/Object");
		jclass stringClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/String");

		int size = parameters.size() + properties.size();
		if (size > 0) {
			objectArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					objectClass, 0);
			stringArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					stringClass, 0);
			int i = 0;
			for (map<std::string, XdmValue*>::iterator iter =
					parameters.begin(); iter != parameters.end(); ++iter, i++) {
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						(iter->second)->getUnderlyingValue(proc));
			}
			for (map<std::string, std::string>::iterator iter =
					properties.begin(); iter != properties.end(); ++iter, i++) {
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->second).c_str()));
			}
		}
		jobject result = (jobject)(
				SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, mID,
						SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
						(sourcefile != NULL ?
								SaxonProcessor::sxn_environ->env->NewStringUTF(sourcefile) :
								NULL),
						(stylesheetfile != NULL ?
								SaxonProcessor::sxn_environ->env->NewStringUTF(
										stylesheetfile) :
								NULL), stringArray, objectArray));
		if (size > 0) {
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(stringArray);
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(objectArray);
		}
		if (result) {
			XdmNode * node = new XdmNode(result);
			node->setProcessor(proc);
			return node;
		}else if(exceptionOccurred()) {
	
			if(proc->exception != NULL) {
				delete proc->exception;
			}
			proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
			proc->exceptionClear();
	   		
     		}
	}
	return NULL;

}


void XsltProcessor::transformFileToFile(const char* source,
		const char* stylesheet, const char* outputfile) {

	if(source == NULL && outputfile == NULL && !stylesheetObject){
		cerr<< "Error: The most recent Stylesheet Object failed. Please check exceptions"<<endl;
		return;
	}
	setProperty("resources", proc->getResourcesDirectory());
	jmethodID mID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"transformToFile",
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)V");
	if (!mID) {
		cerr << "Error: MyClassInDll." << "transformToFile" << " not found\n"
				<< endl;

	} else {
		jobjectArray stringArray = NULL;
		jobjectArray objectArray = NULL;
		jclass objectClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/Object");
		jclass stringClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/String");

		int size = parameters.size() + properties.size();
#ifdef DEBUG
		cerr<<"Properties size: "<<properties.size()<<endl;
		cerr<<"Parameter size: "<<parameters.size()<<endl;
		cerr<<"size:"<<size<<endl;
#endif
		if (size > 0) {
			objectArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					objectClass, 0);
			stringArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					stringClass, 0);
			if (objectArray == NULL) {
				cerr << "objectArray is NULL" << endl;
			}
			if (stringArray == NULL) {
				cerr << "stringArray is NULL" << endl;
			}
			int i = 0;
			for (map<std::string, XdmValue*>::iterator iter =
					parameters.begin(); iter != parameters.end(); ++iter, i++) {

#ifdef DEBUG
				cerr<<"map 1"<<endl;
				cerr<<"iter->first"<<(iter->first).c_str()<<endl;
#endif
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
#ifdef DEBUG
				string s1 = typeid(iter->second).name();
				cerr<<"Type of itr:"<<s1<<endl;
				jobject xx = (iter->second)->getUnderlyingValue(proc);
				if(xx == NULL) {
					cerr<<"value failed"<<endl;
				} else {

					cerr<<"Type of value:"<<(typeid(xx).name())<<endl;
				}
				if((iter->second)->getUnderlyingValue(proc) == NULL) {
					cerr<<"(iter->second)->getUnderlyingValue() is NULL"<<endl;
				}
#endif
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						(iter->second)->getUnderlyingValue(proc));

			}

			for (map<std::string, std::string>::iterator iter =
					properties.begin(); iter != properties.end(); ++iter, i++) {
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->second).c_str()));
			}
		}
		SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, mID,
								SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
								(source != NULL ?
										SaxonProcessor::sxn_environ->env->NewStringUTF(
												source) :
										NULL),
								SaxonProcessor::sxn_environ->env->NewStringUTF(stylesheet),NULL,
								stringArray, objectArray);
		if (size > 0) {
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(stringArray);
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(objectArray);
		}
		
	}

	if(exceptionOccurred()) {
			if(proc->exception != NULL) {
				delete proc->exception;
			}
			proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
			proc->exceptionClear();
	   		
     		}


}


XdmValue * XsltProcessor::getXslMessages(){

jmethodID mID =   (jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"getXslMessages",
					"()[Lnet/sf/saxon/s9api/XdmValue;");
	if (!mID) {
		cerr << "Error: MyClassInDll." << "transformToString" << " not found\n"
				<< endl;

	} else {
jobjectArray results = (jobjectArray)(
			SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, mID));
	int sizex = SaxonProcessor::sxn_environ->env->GetArrayLength(results);

	if (sizex>0) {
		XdmValue * value = new XdmValue();
		
		for (int p=0; p < sizex; ++p) 
		{
			jobject resulti = SaxonProcessor::sxn_environ->env->GetObjectArrayElement(results, p);
			value->addUnderlyingValue(resulti);
		}
		SaxonProcessor::sxn_environ->env->DeleteLocalRef(results);
		return value;
	}
    }
    return NULL;


}

const char * XsltProcessor::transformFileToString(const char* source,
		const char* stylesheet) {
	if(source == NULL && stylesheet == NULL && !stylesheetObject){
		cerr<< "Error: The most recent StylesheetObject failed. Please check exceptions"<<endl;
		return NULL;
	}
	setProperty("resources", proc->getResourcesDirectory());
	jmethodID mID =
			(jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(cppClass,
					"transformToString",
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
	if (!mID) {
		cerr << "Error: MyClassInDll." << "transformFileToString" << " not found\n"
				<< endl;

	} else {
		jobjectArray stringArray = NULL;
		jobjectArray objectArray = NULL;
		jclass objectClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/Object");
		jclass stringClass = lookForClass(SaxonProcessor::sxn_environ->env,
				"java/lang/String");

		int size = parameters.size() + properties.size();
#ifdef DEBUG
		cerr<<"Properties size: "<<properties.size()<<endl;
		cerr<<"Parameter size: "<<parameters.size()<<endl;
#endif
		if (size > 0) {
			objectArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					objectClass, 0);
			stringArray = SaxonProcessor::sxn_environ->env->NewObjectArray((jint) size,
					stringClass, 0);
			if (objectArray == NULL) {
				cerr << "objectArray is NULL" << endl;
			}
			if (stringArray == NULL) {
				cerr << "stringArray is NULL" << endl;
			}
			int i = 0;
			for (map<std::string, XdmValue*>::iterator iter =
					parameters.begin(); iter != parameters.end(); ++iter, i++) {

#ifdef DEBUG
				cerr<<"map 1"<<endl;
				cerr<<"iter->first"<<(iter->first).c_str()<<endl;
#endif
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
#ifdef DEBUG
				string s1 = typeid(iter->second).name();
				cerr<<"Type of itr:"<<s1<<endl;
				jobject xx = (iter->second)->getUnderlyingValue(proc);
				if(xx == NULL) {
					cerr<<"value failed"<<endl;
				} else {

					cerr<<"Type of value:"<<(typeid(xx).name())<<endl;
				}
				if((iter->second)->getUnderlyingValue(proc) == NULL) {
					cerr<<"(iter->second)->getUnderlyingValue() is NULL"<<endl;
				}
#endif
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						(iter->second)->getUnderlyingValue(proc));

			}

			for (map<std::string, std::string>::iterator iter =
					properties.begin(); iter != properties.end(); ++iter, i++) {
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(stringArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->first).c_str()));
				SaxonProcessor::sxn_environ->env->SetObjectArrayElement(objectArray, i,
						SaxonProcessor::sxn_environ->env->NewStringUTF(
								(iter->second).c_str()));
			}
		}

	jstring result = NULL;
	jobject obj =
				(
						SaxonProcessor::sxn_environ->env->CallObjectMethod(cppXT, mID,
								SaxonProcessor::sxn_environ->env->NewStringUTF(cwdXT.c_str()),
								(source != NULL ?
										SaxonProcessor::sxn_environ->env->NewStringUTF(
												source) :
										NULL),
								SaxonProcessor::sxn_environ->env->NewStringUTF(stylesheet),
								stringArray, objectArray));
		if(obj) {
			result = (jstring)obj;
		}		
		if (size > 0) {
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(stringArray);
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(objectArray);
		}
		if (result) {
			const char * str = SaxonProcessor::sxn_environ->env->GetStringUTFChars(result,
					NULL);
			//return "result should be ok";
			return str;
		} else if(exceptionOccurred()) {
			if(proc->exception != NULL) {
				delete proc->exception;
			}
			proc->exception = proc->checkForExceptionCPP(SaxonProcessor::sxn_environ->env, cppClass, NULL);
			proc->exceptionClear();
	   		
     		}
	}

	return NULL;
}


   const char * XsltProcessor::transformToString(){
	if(!stylesheetObject){
		cerr<< "Error: The most recent Stylesheet Object failed or has not been set."<<endl;
		return NULL;
	}
	return transformFileToString(NULL, NULL);
   }


    XdmValue * XsltProcessor::transformToValue(){
	if(!stylesheetObject){
		cerr<< "Error: The most recent Stylesheet Object failed or has not been set."<<endl;
		return NULL;
	}
	return transformFileToValue(NULL, NULL);
   }

    void XsltProcessor::transformToFile(){
	if(!stylesheetObject){
		cerr<< "Error: The most recent Stylesheet Object failed or has not been set."<<endl;
		return;
	}
	transformFileToFile(NULL, NULL, NULL);
   }

const char * XsltProcessor::getErrorMessage(int i ){
 if(proc->exception == NULL) {return NULL;}
 return proc->exception->getErrorMessage(i);
 }

