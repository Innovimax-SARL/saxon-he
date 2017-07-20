#include "XdmValue.h"
#include "XdmItem.h"

     //XdmValue::XdmValue(const XdmValue &other){
	//SaxonProcessor *proc = other.proc; //TODO
	/*char* valueType = NULL; 

	xdmSize=other.xdmSize;
	jValues = other.jValues;
     }*/

     int XdmValue::size(){
	     return xdmSize;	
        }

      XdmValue::XdmValue(jobject val){
		XdmItem * value = new XdmItem(val);
		values.resize(0);//TODO memory issue might occur here
		values.push_back(value);
		xdmSize++; 
		jValues = NULL;
        valueType = NULL;
	}


	XdmValue::~XdmValue() {
		//proc->env->ReleaseObject
		for(int i =0; i< values.size(); i++){
			if(values[i]->getRefCount()<1){
	        		delete values[i];
			}
        	}
		values.clear();
		if(valueType != NULL) {delete valueType;}
		if(jValues && proc != NULL) {
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(jValues);
		}
		xdmSize=0;
	}

     void XdmValue::addXdmItem(XdmItem* val){
	if(val != NULL) {
		values.push_back(val);
		xdmSize++;
		jValues = NULL; //TODO clear jni array from memory if needed
	}	
    }


     XdmValue * XdmValue::addUnderlyingValue(jobject val){
	XdmItem * valuei = new XdmItem(val);
	valuei->setProcessor(proc);
	values.push_back(valuei);
	xdmSize++;
	jValues = NULL; //TODO clear jni array from memory if needed
		
    }




	XdmItem * XdmValue::getHead(){
		if(values.size() >0){
			return values[0];
		} else {
			return NULL;		
		}
	}

	jobject XdmValue::getUnderlyingValue(SaxonProcessor * proci){

		if(jValues == NULL) {	
			proc = proci;	
			jValues;
			int i;
			JNIEnv *env = SaxonProcessor::sxn_environ->env;
			int count = values.size();
			if(count == 0) {
				return NULL;
			}
			jclass objectClass = lookForClass(env,
				"java/lang/Object");
			jValues = (jobjectArray)env->NewObjectArray((jint)count, objectClass,0);

			for(i=0; i<count;i++){
			  env->SetObjectArrayElement(jValues,i,values[i]->getUnderlyingValue(proc));	
			}
		}
		return (jobject)jValues;
	}

	void XdmValue::releaseXdmValue(){
	
	
	
	}

	XdmItem * XdmValue::itemAt(int n){
		/*if(jValues != NULL) {
			values.clear();
			int sizex = SaxonProcessor::sxn_environ->env->GetArrayLength(jvalues);
			for (int p=0; p < sizex; ++p) 
			{
				jobject resulti = SaxonProcessor::sxn_environ->env->GetObjectArrayElement(jValues, p);
				values->addUnderlyingValue(resulti);
			}
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(jValues);
			jValues = NULL;
		}*/
		if(n >= 0 && n< values.size()) {
			return values[n];
		}
		return NULL;
	}

	/**
	* Get the type of the object
	*/
	XDM_TYPE XdmValue::getType(){
		return XDM_VALUE;
	}


     /**
     * Constructor. Create a value from a collection of items
     * @param container - entire container is expected
     */
//TODO
//Errors in codes. I will come back to this later. see guide: http://www.cplusplus.com/articles/SNywvCM9/
	/*template <class ContainerType>
     XdmValue::XdmValue(const ContainerType& container){
	ContainerType::const_iterator current(container.begin()), end(container.end());
        for( ; current != end; ++current)
        {
           std::cout << *current << ' ';
        }


     }*/

     /**
     * Constructor. Create a value from a collection of items
     * @param container - entire container is expected
     */
//TODO
     /*XdmValue::XdmValue(ForwardIteratorType begin, ForwardIteratorType end){



     }*/



/*
* XdmNode Class implementation
*/

      /*  jobject makeXdmValue(SaxonProcessor *proc, JNIEnv *env){
	 if(env == NULL) {
		cerr<<"Error: env is null\n"<<endl;
	 }
	   jclass xdmValueClass = lookForClass(env, "net/sf/saxon/option/cpp/XdmValueForCpp");
	   jmethodID MID_init = findConstructor (env, xdmValueClass, "()V");
 	   jobject xdmValue = (jobject)env->NewObject(xdmValueClass, MID_init);
      		if (!xdmValue) {
	        	cerr<<"Error: failed to allocate an object\n"<<endl;
        		return NULL;
      		}

	
	 return xdmValue;
	}


	jobject makeXdmValue(SaxonProcessor *proc, JNIEnv *env, bool b){ 
	 if(env == NULL) {
		cerr<<"Error: env is null\n"<<endl;
	 }
		jclass xdmValueClass = lookForClass(env, "net/sf/saxon/option/cpp/XdmValueForCpp");
	        jmethodID MID_init = findConstructor (env, xdmValueClass, "(Z)V");
 		jobject xdmValue = (jobject)(env->NewObject(xdmValueClass, MID_init, (jboolean)b));
      		if (!xdmValue) {
	        	cerr<<"Error: failed to allocate an object\n"<<endl;
        		return NULL;
      		}
		return xdmValue; from TLMax. 
	}

	jobject makeXdmValue(SaxonProcessor *proc, JNIEnv *env, int i){ 
	 if(env == NULL) {
		cerr<<"Error: env is null\n"<<endl;
	 }
		jclass xdmValueClass = lookForClass(env, "net/sf/saxon/option/cpp/XdmValueForCpp");
	        jmethodID MID_init = findConstructor (env, xdmValueClass, "(I)V");
 		jobject xdmValue = (jobject)(env->NewObject(xdmValueClass, MID_init, (jint)i));
      		if (!xdmValue) {
	        	cerr<<"Error: failed to allocate an XdmValueForCpp object \n"<<endl;
        		return NULL;
      		}
		return xdmValue;
	}


	jobject makeXdmValue(SaxonProcessor *proc, JNIEnv *env, string type, string valueStr){ 
	 if(env == NULL) {
		cerr<<"Error: env is null\n"<<endl;
	 }
		jclass xdmValueClass = lookForClass(env, "net/sf/saxon/option/cpp/XdmValueForCpp");
	        jmethodID MID_init = findConstructor (env, xdmValueClass, "(Ljava/lang/String;Ljava/lang/String;)V");
		if(!MID_init) {
			cerr<<"methodID not found"<<endl;
		}
 		jobject xdmValue = (jobject)(env->NewObject(xdmValueClass, MID_init, env->NewStringUTF(type.c_str()), env->NewStringUTF(valueStr.c_str())));
		
	
      		if (xdmValue==NULL) {
			const char * errStr = checkForException(env, xdmValueClass, xdmValue);
			cerr<<"Error: failed to allocate an XdmValueValue object";
			if(errStr){	        	
				cerr<< errStr 
			}
			cerr<<"\n"<<endl;
        		return NULL;
      		}
		return xdmValue;
	}


	jobject makeXdmValue(SaxonProcessor *proc, JNIEnv *env, string valueStr){ 
	 if(env == NULL) {
		cerr<<"Error: env is null\n"<<endl;
	 }
		jclass xdmValueClass = lookForClass(env, "net/sf/saxon/option/cpp/XdmValueForCpp");
	        jmethodID MID_init = findConstructor (env, xdmValueClass, "(Ljava/lang/String;Ljava/lang/String;)V");
 		jobject xdmValue = (jobject)(env->NewObject(xdmValueClass, MID_init, env->NewStringUTF("string"), env->NewStringUTF(valueStr.c_str())));

	
      		if (xdmValue==NULL) {
			const char * errStr = checkForException(env, xdmValueClass, xdmValue);
			cerr<<"Error: failed to allocate an XdmValueValue object";
			if(errStr){	        	
				cerr<< errStr 
			}
			cerr<<"\n"<<endl;
        		return NULL;
      		}
		return xdmValue;
	}

  const char * XdmValue::getErrorMessage(int i){
	if(proc->exception== NULL) return NULL;
	return proc->exception->getErrorMessage(i);
    }

    const char * XdmValue::getErrorCode(int i) {
	if(proc->exception== NULL) return NULL;
	return proc->exception->getErrorCode(i);
     }

  int XdmValue::exceptionCount(){
		return proc->exception->count();
	}

   */

	


