////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////#ifndef PHP_SAXON_H

#ifndef PHP_SAXON_H
#define PHP_SAXON_H


extern "C" {


    #include "php.h"
}

#include <phpcpp.h>
#include <unistd.h>
#include <iostream>
#include <jni.h>
#include "SaxonProcessor.h"
#include "XdmValue.h"
#include "XdmItem.h"
#include "XdmNode.h"
#include "XdmAtomicValue.h"
#include <vector>








#define PHP_SAXON_EXTNAME  "Saxon/C"
#define PHP_SAXON_EXTVER   "1.1.0"

class PHP_SaxonProcessor : public Php::Base
{

private:

	 SaxonProcessor* saxonProcessor;
	bool _license;
	const char* _cwd;
	std::vector<const char *> nativeFuncs;

public:





/*
 * Class:     com_saxonica_functions_extfn_PhpCall_PhpFunctionCall
 * Method:    _phpCall
 * Signature: ([Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Object;
 */

jobject JNICALL phpNativeCall
  (JNIEnv *env, jstring funcName, jobjectArray arguments, jobjectArray arrayTypes);





	
	    /**
     		*  Constructor
     		*
     		*  @param  boolean   optional to accept one parameter - license flag
		*  @param  boolean   optional to accept two parameter - license flag
     		*  @return int     New value
     		*/
	PHP_SaxonProcessor() = default;

	void __construct(Php::Parameters &params)
    {
        // copy first parameter (if available)
        if (params.size()< 2) {
		if(params.size() == 1) {
			_license = params[0];
		} else {
			_license = false;	
		}
		saxonProcessor = new SaxonProcessor(_license);
#if !(defined (__linux__) || (defined (__APPLE__) && defined(__MACH__)))
	    TCHAR s[256];

            // --
            DWORD a = GetCurrentDirectory(256, s);
	    const size_t newsize = wcslen(s)*2;
	    char* cwd = new char[newsize];
	    wcstombs_s(0, cwd, newsize, s, _TRUNCATE);
	    // -- code above returns the apache installtion directory as the CWD

	    char* cwd2;

	    //php_error(E_WARNING,cwd2);

	    saxonProcessor->setcwd(cwd2);
	    // -- code above tries to use VCWD_GETCWD but there is a linkage error
#else

	    char cwd[256];

	    getcwd(cwd, sizeof(cwd));
	    if(cwd == NULL) {
	     //php_error(E_WARNING,"cwd is null");
	   }else {
             //php_error(E_WARNING,cwd);

	    saxonProcessor->setcwd(cwd);
          }
#endif
	}
	 else if (params.size() == 2) {
		 _license = params[0];
		_cwd = params[1];
		saxonProcessor = new SaxonProcessor(_license);
		saxonProcessor->setcwd((const char *)_cwd);
	}
    }

	virtual ~PHP_SaxonProcessor(){
		delete saxonProcessor;
	}


    	Php::Value createAtomicValue(Php::Parameters &params);
        Php::Value  parseXmlFromString(Php::Parameters &params);
        Php::Value  parseXmlFromFile(Php::Parameters &params);
         void setcwd(Php::Parameters &params);
//    PHP_METHOD(SaxonProcessor,  importDocument);
       void setResourcesDirectory(Php::Parameters &params);
    void registerPHPFunction(Php::Parameters &params);
 //   PHP_METHOD(SaxonProcessor,  getXslMessages);
       void  setConfigurationProperty(Php::Parameters &params);
    Php::Value newXsltProcessor();
    Php::Value newXQueryProcessor();
    Php::Value newXPathProcessor();
    Php::Value newSchemaValidator();
    Php::Value version();

};

class PHP_XsltProcessor : public Php::Base
{
private:
	XsltProcessor *xsltProcessor;

public:
    PHP_XsltProcessor() = default;

    /*void __construct(Php::Parameters &params){

    }*/

    virtual ~PHP_XsltProcessor(){
	delete xsltProcessor;
    }

    PHP_XsltProcessor(XsltProcessor * value) {
	xsltProcessor = value;
    }

    void transformFileToFile(Php::Parameters &params);

    Php::Value  transformFileToString(Php::Parameters &params);

    Php::Value  transformFileToValue(Php::Parameters &params);
    Php::Value  transformToString(Php::Parameters &params);
    Php::Value  transformToValue(Php::Parameters &params);
    void  transformToFile(Php::Parameters &params);
    void compileFromFile(Php::Parameters &params);
    void compileFromValue(Php::Parameters &params);
    void compileFromString(Php::Parameters &params);
    void  setOutputFile(Php::Parameters &params);
    void  setSourceFromFile(Php::Parameters &params);
    void  setSourceFromXdmValue(Php::Parameters &params);
    void  setParameter(Php::Parameters &params);
    void  setProperty(Php::Parameters &params);
    void  clearParameters();
    void  clearProperties();
    void  exceptionClear();
    Php::Value  exceptionOccurred();
    Php::Value  getErrorCode(Php::Parameters &params);
    Php::Value  getErrorMessage(Php::Parameters &params);
    Php::Value  getExceptionCount();


};


class PHP_XQueryProcessor : public Php::Base
{

private:
	XQueryProcessor *xqueryProcessor;

public:
    PHP_XQueryProcessor() = default;

    

    virtual ~PHP_XQueryProcessor(){
	delete xqueryProcessor;
    }

    PHP_XQueryProcessor(XQueryProcessor * value) {
	xqueryProcessor = value;
    }

    void  setQueryContent(Php::Parameters &params);
    void  setContextItem(Php::Parameters &params);
    void  setContextItemFromFile(Php::Parameters &params);
    void  setParameter(Php::Parameters &params);
    void  setProperty(Php::Parameters &params);
    void  setOutputFile(Php::Parameters &params);
    void  setQueryFile(Php::Parameters &params);
    void  setQueryBaseURI(Php::Parameters &params);
    void  declareNamespace(Php::Parameters &params);
    void  clearParameters();
    void  clearProperties();

    Php::Value  runQueryToValue();
    Php::Value  runQueryToString();
    Php::Value  runQueryToFile(Php::Parameters &params);
    void  exceptionClear();
    Php::Value  exceptionOccurred();
    Php::Value  getErrorCode(Php::Parameters &params);
    Php::Value  getErrorMessage(Php::Parameters &params);
    Php::Value  getExceptionCount();

};
    

   class PHP_XPathProcessor : public Php::Base
{


private:
	XPathProcessor * xpathProcessor;

public:
    PHP_XPathProcessor() = default;

    

    virtual ~PHP_XPathProcessor(){
	delete xpathProcessor;
    }

    PHP_XPathProcessor(XPathProcessor * value) {
	xpathProcessor = value;
    }

    void setContextItem(Php::Parameters &params);
    void setContextFile(Php::Parameters &params);
    void setBaseURI(Php::Parameters &params);
    Php::Value effectiveBooleanValue(Php::Parameters &params);
    Php::Value evaluate(Php::Parameters &params);
    Php::Value evaluateSingle(Php::Parameters &params);
    void declareNamespace(Php::Parameters &params);
    void  setParameter(Php::Parameters &params);
    void  setProperty(Php::Parameters &params);
    void  clearParameters();
    void  clearProperties();
    void  exceptionClear();
    Php::Value  exceptionOccurred();
    Php::Value  getErrorCode(Php::Parameters &params);
    Php::Value  getErrorMessage(Php::Parameters &params);
    Php::Value  getExceptionCount();

};

   

   class PHP_SchemaValidator : public Php::Base
{


private:
	SchemaValidator * schemaValidator;

public:
    PHP_SchemaValidator() = default;

    

    virtual ~PHP_SchemaValidator(){
	delete schemaValidator;
    }

    PHP_SchemaValidator(SchemaValidator * value) {
	schemaValidator = value;
    }

	void setOutputFile(Php::Parameters &params);

	void setSourceNode(Php::Parameters &params);

	void registerSchemaFromFile(Php::Parameters &params);

	void registerSchemaFromString(Php::Parameters &params);

	void validate(Php::Parameters &params);

	Php::Value validateToNode(Php::Parameters &params);	

	Php::Value getValidationReport();

    	void  setParameter(Php::Parameters &params);
    	void  setProperty(Php::Parameters &params);
    	void  clearParameters();
    	void  clearProperties();
    	void  exceptionClear();
    	Php::Value  exceptionOccurred();
    	Php::Value  getErrorCode(Php::Parameters &params);
    	Php::Value  getErrorMessage(Php::Parameters &params);
    	Php::Value  getExceptionCount();

	
};


   
	

/*     ============== PHP Interface of   XdmValue =============== */

   class PHP_XdmValue : public Php::Base
{
protected:
	XdmValue * _value;

public:
	PHP_XdmValue() = default;

	virtual ~PHP_XdmValue(){
		//std::cerr<<"checkpoint 1 destructor XdmValue pointer: "<<(&_value)<<std::endl;
		if(_value != NULL) {
			_value->decrementRefCount();
			//std::cerr<<"checkpoint 3destructor XdmValue refCount: "<<_value->getRefCount()<<std::endl;
    			if(_value != NULL && _value->getRefCount()< 1){
    				delete _value;
    			}
		}
	}

	/*void __construct(Php::Parameters &params)
   	 {

		//if (!params.empty()) _value = params[0];

	}*/
	PHP_XdmValue(XdmValue * value) {
		_value = value;
	}

	XdmValue * getInternal(){
		return _value;
	}

	Php::Value getHead();

	Php::Value itemAt(Php::Parameters &params);

	Php::Value size();

	void addXdmItem(Php::Parameters &params);


	

};

    


/*     ============== PHP Interface of   XdmItem =============== */

   class PHP_XdmItem : public PHP_XdmValue
{

private:


public:

	PHP_XdmItem() {}
	
	PHP_XdmItem(XdmItem * nodei) {
		_value = (XdmValue*)nodei;
	}

	virtual ~PHP_XdmItem(){	
	}

	virtual Php::Value isAtomic(){
		return ((XdmItem *)_value)->isAtomic();
	}

	virtual Php::Value isNode(){
		return ((XdmItem *)_value)->getType() == XDM_NODE;
	}

	Php::Value getAtomicValue();

	Php::Value getNodeValue();

	virtual Php::Value getStringValue(){
		return ((XdmItem*)_value)->getStringValue();

	}

	Php::Value __toString(){
		return getStringValue();
	}


};

    	

/*     ============== PHP Interface of   XdmNode =============== */

   class PHP_XdmNode : public PHP_XdmItem
{

private:
	

public:
	
	PHP_XdmNode(XdmNode * nodei) {
		_value = (XdmValue*)nodei;
	}

	virtual ~PHP_XdmNode(){
		
	}

	Php::Value __toString()
   	 {
        return getStringValue();
    	}


	Php::Value getStringValue(){
std::cerr<<"checkpoint pp"<<std::endl;
if(_value == NULL) {
std::cerr<<"checkpoint -- _value is NULL"<<std::endl;
}
		if(((XdmItem*)_value)->getStringValue() == NULL) {
			std:cerr<<"Error in stringValue"<<std::endl;
		} else {
			std::cerr<<"checkpoint zz: "<<(((XdmItem*)_value)->getStringValue())<<std::endl;
		}
		return ((XdmItem*)_value)->getStringValue();

	}

	Php::Value getNodeKind();

	Php::Value getNodeName();



	Php::Value getChildCount();

	Php::Value getAttributeCount();

	Php::Value getChildNode(Php::Parameters &params);

	//TODO - mention that this is a new method in Saxon/C 1.1.0
	Php::Value getChildren();

	Php::Value getParent();

	Php::Value getAttributeNode(Php::Parameters &params);

	//TODO - mention that this is a new method in Saxon/C 1.1.0
	Php::Value getAttributeNodes();

	Php::Value getAttributeValue(Php::Parameters &params);

 	Php::Value isAtomic(){
		return false;
	}

	Php::Value isNode(){
		return true;
	}



};

    
    

/*     ============== PHP Interface of   XdmAtomicValue =============== */

   class PHP_XdmAtomicValue : public PHP_XdmItem
{

private:
	

public:
	
	PHP_XdmAtomicValue(XdmAtomicValue * value) {
		_value = (XdmValue *)value;
	}

	virtual ~PHP_XdmAtomicValue(){
	}

	Php::Value getStringValue();

	Php::Value getBooleanValue();

	Php::Value getLongValue();

	Php::Value getDoubleValue();

	Php::Value isAtomic();

	Php::Value __toString()
   	 {
        return getStringValue();
    	}


	Php::Value isNode(){
		return false;
	}

};

    


#endif /* PHP_SAXON_H */

















