////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////#ifndef PHP_SAXON_H

#ifndef PHP_SAXON_H
#define PHP_SAXON_H

#include <phpcpp.h>
#include <unistd.h>
#include <iostream>
#include "SaxonProcessor.h"
#include "XdmValue.h"
#include "XdmItem.h"
#include "XdmNode.h"
#include "XdmAtomicValue.h"







#define PHP_SAXON_EXTNAME  "Saxon/C"
#define PHP_SAXON_EXTVER   "1.1.0"

class PHP_SaxonProcessor : public Php::Base
{

private:

	 SaxonProcessor * saxonProcessor;
	bool _license;
	const char* _cwd;

public:
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
        if (params.size()== 1) {
		_license = params[0];
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
		saxonProcessor->setcwd(_cwd);
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
//    PHP_METHOD(SaxonProcessor,  registerPHPFunction);
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



/*
//PHP_METHOD(XsltProcessor,  __construct);
    PHP_METHOD(XsltProcessor,  __destruct);
    PHP_METHOD(XsltProcessor,  transformFileToFile);
    PHP_METHOD(XsltProcessor,  transformFileToString);
    PHP_METHOD(XsltProcessor,  transformFileToValue);
    PHP_METHOD(XsltProcessor,  transformToString);
    PHP_METHOD(XsltProcessor,  transformToValue);
    PHP_METHOD(XsltProcessor,  transformToFile);
    PHP_METHOD(XsltProcessor, compileFromFile);
    PHP_METHOD(XsltProcessor, compileFromValue);
    PHP_METHOD(XsltProcessor, compileFromString);
    PHP_METHOD(XsltProcessor,  setOutputFile);
    PHP_METHOD(XsltProcessor,  setSourceFromFile);
    PHP_METHOD(XsltProcessor,  setSourceFromXdmValue);
    PHP_METHOD(XsltProcessor,  setParameter);
    PHP_METHOD(XsltProcessor,  setProperty);
    PHP_METHOD(XsltProcessor,  clearParameters);
    PHP_METHOD(XsltProcessor,  clearProperties);
    PHP_METHOD(XsltProcessor,  exceptionClear);
    PHP_METHOD(XsltProcessor,  exceptionOccurred);
    PHP_METHOD(XsltProcessor,  getErrorCode);
    PHP_METHOD(XsltProcessor,  getErrorMessage);
    PHP_METHOD(XsltProcessor,  getExceptionCount);
*/

};


class PHP_XQueryProcesor : public Php::Base
{

/*
// PHP_METHOD(XQueryProcesor,  __construct);
    PHP_METHOD(XQueryProcesor,  __destruct);
    PHP_METHOD(XQueryProcessor,  setQueryContent);
    PHP_METHOD(XQueryProcessor,  setContextItem);
    PHP_METHOD(XQueryProcessor,  setContextItemFromFile);
    PHP_METHOD(XQueryProcessor,  setParameter);
    PHP_METHOD(XQueryProcessor,  setProperty);
    PHP_METHOD(XQueryProcessor,  clearParameters);
    PHP_METHOD(XQueryProcessor,  clearProperties);
   // PHP_METHOD(XQueryProcessor, setOutputFile);
    PHP_METHOD(XQueryProcessor, runQueryToValue);
    PHP_METHOD(XQueryProcessor, runQueryToString);
    PHP_METHOD(XQueryProcessor, runQueryToFile);
    PHP_METHOD(XQueryProcessor, setQueryFile);
    PHP_METHOD(XQueryProcessor, setQueryBaseURI);
    PHP_METHOD(XQueryProcessor, declareNamespace);
    PHP_METHOD(XQueryProcessor,  exceptionClear);
    PHP_METHOD(XQueryProcessor,  exceptionOccurred);
    PHP_METHOD(XQueryProcessor,  getErrorCode);
    PHP_METHOD(XQueryProcessor,  getErrorMessage);
    PHP_METHOD(XQueryProcessor,  getExceptionCount);
*/

};
    

   class PHP_XPathProcesor : public Php::Base
{
/*
// PHP_METHOD(XPathProcessor,  __construct);
    PHP_METHOD(XPathProcessor,  __destruct);
    PHP_METHOD(XPathProcessor,  setContextItem);
    PHP_METHOD(XPathProcessor,  setContextFile);
    PHP_METHOD(XQueryProcessor, setBaseURI);
    PHP_METHOD(XPathProcessor,  effectiveBooleanValue);
    PHP_METHOD(XPathProcessor,  evaluate);
    PHP_METHOD(XPathProcessor,  evaluateSingle);
    PHP_METHOD(XPathProcessor, declareNamespace);
    PHP_METHOD(XPathProcessor,  setParameter);
    PHP_METHOD(XPathProcessor,  setProperty);
    PHP_METHOD(XPathProcessor,  clearParameters);
    PHP_METHOD(XPathProcessor,  clearProperties);
    PHP_METHOD(XPathProcessor,  exceptionClear);
    PHP_METHOD(XPathProcessor,  exceptionOccurred);
    PHP_METHOD(XPathProcessor,  getErrorCode);
    PHP_METHOD(XPathProcessor,  getErrorMessage);
    PHP_METHOD(XPathProcessor,  getExceptionCount);
*/

};

   

   class PHP_SchemaValidator : public Php::Base
{

/*
 // PHP_METHOD(SchemaValidator,  __construct);
    PHP_METHOD(SchemaValidator,  __destruct);
    PHP_METHOD(SchemaValidator,  setSourceNode);
    PHP_METHOD(SchemaValidator,  setOutputFile);
    PHP_METHOD(SchemaValidator, registerSchemaFromFile);
    PHP_METHOD(SchemaValidator, registerSchemaFromString);
    PHP_METHOD(SchemaValidator, validate); 
    PHP_METHOD(SchemaValidator, validateToNode);
    PHP_METHOD(SchemaValidator, getValidationReport);
    PHP_METHOD(SchemaValidator,  setParameter);
    PHP_METHOD(SchemaValidator,  setProperty);
    PHP_METHOD(SchemaValidator,  clearParameters);
    PHP_METHOD(SchemaValidator,  clearProperties);
    PHP_METHOD(SchemaValidator,  exceptionClear);
    PHP_METHOD(SchemaValidator,  exceptionOccurred);
    PHP_METHOD(SchemaValidator,  getErrorCode);
    PHP_METHOD(SchemaValidator,  getErrorMessage);
    PHP_METHOD(SchemaValidator,  getExceptionCount);
*/
};


   
	

/*     ============== PHP Interface of   XdmValue =============== */

   class PHP_XdmValue : public Php::Base
{

/*
PHP_METHOD(XdmValue,  __construct);
    PHP_METHOD(XdmValue,  __destruct);
    PHP_METHOD(XdmValue,  getHead);
    PHP_METHOD(XdmValue,  itemAt);
    PHP_METHOD(XdmValue,  size);
    PHP_METHOD(XdmValue, addXdmItem);
*/

};

    


/*     ============== PHP Interface of   XdmItem =============== */

   class PHP_XdmItem : public Php::Base
{

/*
PHP_METHOD(XdmItem,  __construct);
    PHP_METHOD(XdmItem,  __destruct);
    PHP_METHOD(XdmItem,  getStringValue);
    PHP_METHOD(XdmItem,  isAtomic);
    PHP_METHOD(XdmItem,  isNode);
    PHP_METHOD(XdmItem,  getAtomicValue);
    PHP_METHOD(XdmItem,  getNodeValue);
*/

};

    	

/*     ============== PHP Interface of   XdmNode =============== */

   class PHP_XdmNode : public Php::Base
{

/*
PHP_METHOD(XdmNode,  __construct);
    PHP_METHOD(XdmNode,  __destruct);
    PHP_METHOD(XdmNode,  getStringValue);
    PHP_METHOD(XdmNode, getNodeKind);
    PHP_METHOD(XdmNode, getNodeName);
    PHP_METHOD(XdmNode,  isAtomic);
    PHP_METHOD(XdmNode,  getChildCount);   
    PHP_METHOD(XdmNode,  getAttributeCount); 
    PHP_METHOD(XdmNode,  getChildNode);
    PHP_METHOD(XdmNode,  getParent);
    PHP_METHOD(XdmNode,  getAttributeNode);
    PHP_METHOD(XdmNode,  getAttributeValue);
*/

};

    
    

/*     ============== PHP Interface of   XdmAtomicValue =============== */

   class PHP_XdmAtomicValue : public Php::Base
{
/*
PHP_METHOD(XdmAtomicValue,  __construct);
    PHP_METHOD(XdmAtomicValue,  __destruct);
    PHP_METHOD(XdmAtomicValue,  getStringValue);
    PHP_METHOD(XdmAtomicValue,  getBooleanValue);
    PHP_METHOD(XdmAtomicValue,  getDoubleValue);
    PHP_METHOD(XdmAtomicValue,  getLongValue);
    PHP_METHOD(XdmAtomicValue,  isAtomic);
*/

};

    


#endif /* PHP_SAXON_H */

















