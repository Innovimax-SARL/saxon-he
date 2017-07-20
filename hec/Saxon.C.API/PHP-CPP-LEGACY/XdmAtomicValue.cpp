
//

#include "XdmAtomicValue.h"

    XdmAtomicValue::XdmAtomicValue():XdmItem(){}

    XdmAtomicValue::XdmAtomicValue(const XdmAtomicValue &aVal): XdmItem(aVal){
	valType = aVal.valType;

    }

   
	

    XdmAtomicValue::XdmAtomicValue(jobject obj):XdmItem(obj){
    }

    XdmAtomicValue::XdmAtomicValue(jobject obj, string ty):XdmItem(obj){
	valType = ty;
    }

    void XdmAtomicValue::setType(string ty){
	valType = ty;
    }

    string XdmAtomicValue::getPrimitiveTypeName(){
	if(!valType.empty()) {
		return valType;	
	}
	
	if(proc != NULL) {
		jclass xdmUtilsClass = lookForClass(SaxonProcessor::sxn_environ->env, "net/sf/saxon/option/cpp/XdmUtils");
		jmethodID xmID = (jmethodID) SaxonProcessor::sxn_environ->env->GetStaticMethodID(xdmUtilsClass,"getPrimitiveTypeName",
					"(Lnet/sf/saxon/s9api/XdmAtomicValue;)Ljava/lang/String;");
		if (!xmID) {
			std::cerr << "Error: SaxonDll." << "getPrimitiveTypeName"
				<< " not found\n" << std::endl;
			return "";
		}
		jstring result = (jstring)(SaxonProcessor::sxn_environ->env->CallStaticObjectMethod(xdmUtilsClass, xmID,value->xdmvalue));
		if(result) {
			const char * stri = SaxonProcessor::sxn_environ->env->GetStringUTFChars(result,
					NULL);
		
		//SaxonProcessor::sxn_environ->env->DeleteLocalRef(result);

			return stri;
		}

	} 
	return "Q{http://www.w3.org/2001/XMLSchema}anyAtomicType";	
	
    }

    bool XdmAtomicValue::getBooleanValue(){
	if(proc != NULL) {
		jclass xdmNodeClass = lookForClass(SaxonProcessor::sxn_environ->env, "net/sf/saxon/s9api/XdmAtomicValue");
		jmethodID bmID = (jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(xdmNodeClass,
					"getBooleanValue",
					"()Z");
		if (!bmID) {
			std::cerr << "Error: MyClassInDll." << "getBooleanValue"
				<< " not found\n" << std::endl;
			return false;
		} else {
			jboolean result = (jboolean)(SaxonProcessor::sxn_environ->env->CallBooleanMethod(value->xdmvalue, bmID));
			if(result) {
				return (bool)result;
			}
		}
	} else {
		std::cerr<<"Error: Processor not set in XdmAtomicValue"<<std::endl;
	}
	return false;
    }

    double XdmAtomicValue::getDoubleValue(){
	if(proc != NULL) {
		jclass xdmNodeClass = lookForClass(SaxonProcessor::sxn_environ->env, "net/sf/saxon/s9api/XdmAtomicValue");
		jmethodID bmID = (jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(xdmNodeClass,
					"getDoubleValue",
					"()D");
		if (!bmID) {
			std::cerr << "Error: MyClassInDll." << "getDecimalValue"
				<< " not found\n" << std::endl;
			return 0;
		} else {
			jdouble result = (jdouble)(SaxonProcessor::sxn_environ->env->CallDoubleMethod(value->xdmvalue, bmID));
			if(result) {
				return (double)result;
			}
checkForException(*(SaxonProcessor::sxn_environ), xdmNodeClass, NULL);
		}
	} else {
		std::cerr<<"Error: Processor not set in XdmAtomicValue"<<std::endl;
	}
	return 0;
    }



    const char * XdmAtomicValue::getStringValue(){
	if(proc != NULL) {
		return XdmItem::getStringValue(proc);
	}
	return NULL;
    }

    long XdmAtomicValue::getLongValue(){
		if(proc != NULL) {
		jclass xdmNodeClass = lookForClass(SaxonProcessor::sxn_environ->env, "net/sf/saxon/s9api/XdmAtomicValue");
		jmethodID bmID = (jmethodID) SaxonProcessor::sxn_environ->env->GetMethodID(xdmNodeClass,
					"getLongValue",
					"()J");
		if (!bmID) {
			std::cerr << "Error: MyClassInDll." << "getLongValue"
				<< " not found\n" << std::endl;
			return 0;
		} else {
			jlong result = (jlong)(SaxonProcessor::sxn_environ->env->CallObjectMethod(value->xdmvalue, bmID));
			if(result) {
				return (long)result;
			}
		}
	} else {
		std::cerr<<"Error: Processor not set in XdmAtomicValue"<<std::endl;
	}
	return 0;
     }
