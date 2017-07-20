#include "phpcpp_saxon.h"

JNINativeMethod phpMethods[] =
{
    {
         "_phpCall",
         "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Object;",
         (void *)&PHP_SaxonProcessor::phpNativeCall
    }
};

void PHP_SaxonProcessor::setResourcesDirectory(Php::Parameters &params)
{
    
    const char * dirStr;
    int len;

    if (!params.empty()) {
        dirStr = params[0];
        if(dirStr != NULL) {
            saxonProcessor->setResourcesDirectory(dirStr);
        }
    }
}



Php::Value  PHP_SaxonProcessor::parseXmlFromString(Php::Parameters &params)
{
   
    const char * source;
    int len1;

  
    if (!params.empty()) {
	source = params[0];
        XdmNode* node = saxonProcessor->parseXmlFromString(source);
	PHP_XdmNode * php_node = new PHP_XdmNode(node);
	node->incrementRefCount(); 
	return Php::Object("Saxon\\XdmNode", php_node);
        
    }
}


Php::Value  PHP_SaxonProcessor::parseXmlFromFile(Php::Parameters &params)
{
   
    const char * source;
    int len1;

    if (!params.empty()) {
	source = params[0];
        XdmNode* node = saxonProcessor->parseXmlFromFile(source);
	PHP_XdmNode * php_xdmNode = new PHP_XdmNode(node);
	node->incrementRefCount();      
	return Php::Object("Saxon\\XdmNode", php_xdmNode);
    }
    return NULL;
}


void PHP_SaxonProcessor::registerPHPFunction(Php::Parameters &params){

 	if (!params.empty() && params.size() == 1) {
		const char * func = params[0];
		nativeFuncs.push_back(func);
		saxonProcessor->registerNativeMethods(SaxonProcessor::sxn_environ->env, "com/saxonica/functions/extfn/PhpCall$PhpFunctionCall",
    phpMethods, sizeof(phpMethods) / sizeof(phpMethods[0]));
	}

Php::Value data = Php::call("userFunction", "something");
	
	
if(data == NULL) {
std::cerr<<"checkpoint in phpNativeCall - data is NULL"<<std::endl;
	
} else {
std::cerr<<"checkpoint in phpNativeCall - data is not null"<<std::endl;
}

}

jobject JNICALL PHP_SaxonProcessor::phpNativeCall
  (JNIEnv *env, jstring funcName, jobjectArray arguments, jobjectArray arrayTypes){


	const char *nativeString = SaxonProcessor::sxn_environ->env->GetStringUTFChars(funcName, NULL);

	Php::Value callback = nativeString;
	zval namei;
	Php::Value data = Php::call(nativeString, "something");
	
	SaxonProcessor::sxn_environ->env->ReleaseStringUTFChars(funcName, nativeString);
if(data == NULL) {

	return NULL;
} else {

const char * dataChar = data; 
jstring jstrBuf = SaxonProcessor::sxn_environ->env->NewStringUTF(dataChar);
	return funcName;
}
}


Php::Value PHP_SaxonProcessor::createAtomicValue(Php::Parameters &params)
{
	Php::Value _value = params[0];
	Php::Type _type = ((Php::Value)params[0]).type();
    XdmAtomicValue * xdmValue = NULL;
   PHP_XdmAtomicValue * php_atomicValue =  NULL;
    SaxonProcessor * proc = saxonProcessor;
 

	if(_value.isBool()) {
                //bVal = Z_BVAL_P(zvalue);
                xdmValue = proc->makeBooleanValue((bool)_value);

	} else if(_value.isNumeric()){
           
		 xdmValue = proc->makeIntegerValue((int)_value);
	} else if(_value.isString()){
           
                xdmValue = proc->makeStringValue((const char*)_value);
	} else if(_value.isNull()) {
            
                xdmValue = new XdmAtomicValue();
	} else if(_value.isFloat()){         
		xdmValue = proc->makeDoubleValue((double)_value);
	} else {
               
           // case IS_ARRAY:
                // TODO: Should not be expected. Do this some other way
                //break;
           // case IS_OBJECT:
                // TODO: implement this
                //break;
           
                throw Php::Exception("Unknown type specified in XdmValue");
                //return NULL;
        }
	
        php_atomicValue = new PHP_XdmAtomicValue(xdmValue);
	xdmValue->incrementRefCount(); 
	return Php::Object("Saxon\\XdmAtomicValue", php_atomicValue);
        
   
}


Php::Value PHP_SaxonProcessor::newXPathProcessor()
{ 
   	XPathProcessor * xpathProc = saxonProcessor->newXPathProcessor();
	PHP_XPathProcessor * php_xpathProc = new PHP_XPathProcessor(xpathProc);      
	return Php::Object("Saxon\\XPathProcessor", php_xpathProc);
          
}

Php::Value PHP_SaxonProcessor::newXsltProcessor()
{
   	XsltProcessor * xsltProc = saxonProcessor->newXsltProcessor();
	PHP_XsltProcessor * php_xsltProc = new PHP_XsltProcessor(xsltProc);     
	return Php::Object("Saxon\\XsltProcessor", php_xsltProc);
     
}

Php::Value PHP_SaxonProcessor::newXQueryProcessor()
{
   	XQueryProcessor * xqueryProc = saxonProcessor->newXQueryProcessor();
	PHP_XQueryProcessor * php_xqueryProc = new PHP_XQueryProcessor(xqueryProc);     
	return Php::Object("Saxon\\XQueryProcessor", php_xqueryProc);   
 }

Php::Value PHP_SaxonProcessor::newSchemaValidator()
{
   	SchemaValidator * schemaVal = saxonProcessor->newSchemaValidator();
	PHP_SchemaValidator * php_schema = new PHP_SchemaValidator(schemaVal);    
	return Php::Object("Saxon\\SchemaValidator", php_schema);   
	
}


Php::Value PHP_SaxonProcessor::version()
{
	
    return saxonProcessor->version();
 
}

void PHP_SaxonProcessor::setcwd(Php::Parameters &params)
{
	 if (!params.empty()) {
		_cwd = params[0];
		saxonProcessor->setcwd((const char *)_cwd);
	}
}

void PHP_SaxonProcessor::setConfigurationProperty(Php::Parameters &params)
{
    const char* name;
    int len1;
    const char* value;
    int len2;
    if (params.size()== 2) {
	name = params[0];
	value = params[1];
        saxonProcessor->setConfigurationProperty(name, value);
    }
    
}

void PHP_XsltProcessor::transformFileToFile(Php::Parameters &params){

    const char* infilename;
    const char* styleFileName;
    const char* outfileName;
    if (params.size()== 3) {
	infilename = params[0];
	styleFileName = params[1];
	outfileName = params[3];
 	xsltProcessor->transformFileToFile(infilename, styleFileName, outfileName);
        if(xsltProcessor->exceptionOccurred()) {
     	  // TODO: throw exception
        }
    }
}

Php::Value  PHP_XsltProcessor::transformFileToString(Php::Parameters &params){

    const char* infilename;
    const char* styleFileName;
    if (params.size()== 2) {
	infilename = params[0];
	styleFileName = params[1];
 	const char * result = xsltProcessor->transformFileToString(infilename, styleFileName);
        if(result != NULL) {
     	  return result;
        }
    }
    return NULL;
}

Php::Value  PHP_XsltProcessor::transformFileToValue(Php::Parameters &params){
    const char* infilename;
    const char* styleFileName;
    if (params.size()== 2) {
	infilename = params[0];
	styleFileName = params[1];
 	XdmValue * node = xsltProcessor->transformFileToValue(infilename, styleFileName);
        if(node != NULL) {
		PHP_XdmValue * php_xdmValue = new PHP_XdmValue(node);
		node->incrementRefCount();     
		return Php::Object("Saxon\\XdmValue", php_xdmValue);
        }
    }
    return NULL;
}
Php::Value  PHP_XsltProcessor::transformToString(Php::Parameters &params){

	if (params.size()>0) {
		throw Php::Exception("Wrong number of arguments");
	
	}
/////
//zval retval;
Php::Value data = NULL;//do_exec(NULL, "userFunction", 0, NULL);
	
	
	
if(data == NULL) {
std::cerr<<"checkpoint in phpNativeCallYYYY - data is NULL"<<std::endl;
	return NULL;
} else {
std::cerr<<"checkpoint in phpNativeCallYYYYs - data is not null"<<data<<std::endl;

}
//////

	const char * result = xsltProcessor->transformToString();
        if(result != NULL) {
            return result;
        } 
	return NULL;

}
Php::Value  PHP_XsltProcessor::transformToValue(Php::Parameters &params){


	if (params.size()>0) {
		throw Php::Exception("Wrong number of arguments");
	
	}

	XdmValue * node = xsltProcessor->transformToValue();
        if(node != NULL) {
		PHP_XdmValue * php_xdmValue = new PHP_XdmValue(node); 
		node->incrementRefCount();    
		return Php::Object("Saxon\\XdmValue", php_xdmValue);
        }
	return NULL;
}
void  PHP_XsltProcessor::transformToFile(Php::Parameters &params){

	if (params.size()>0) {
		throw Php::Exception("Wrong number of arguments");
	
	}

	xsltProcessor->transformToFile();
        
}
void PHP_XsltProcessor::compileFromFile(Php::Parameters &params){
    const char* styleFileName;
    if (params.size()== 1) {

	styleFileName = params[0];
 	xsltProcessor->compileFromFile(styleFileName);

    }
}
void PHP_XsltProcessor::compileFromValue(Php::Parameters &params){
   
    if (params.size()== 1) {

	PHP_XdmValue * node = (PHP_XdmValue *)params[0].implementation();
	if(node != NULL) {
 		xsltProcessor->compileFromXdmNode((XdmNode *)node->getInternal());
	}

    }
}
void PHP_XsltProcessor::compileFromString(Php::Parameters &params){
    if (params.size()== 1) {

	const char * stylesheet = params[0];
	if(stylesheet != NULL) {
 		xsltProcessor->compileFromString(stylesheet);
	}

    }

}
void  PHP_XsltProcessor::setOutputFile(Php::Parameters &params){
if (params.size()== 1) {

	const char * outputFilename = params[0];
	if(outputFilename != NULL) {
 		xsltProcessor->setOutputFile(outputFilename);
	}

    }
}
void  PHP_XsltProcessor::setSourceFromFile(Php::Parameters &params){
	if (params.size()== 1) {

		const char * outputFilename = params[0];
		if(outputFilename != NULL) {
 			xsltProcessor->setSourceFromFile(outputFilename);
		}

   	 }
}


void  PHP_XsltProcessor::setSourceFromXdmValue(Php::Parameters &params){
	if (params.size()== 1) {

		PHP_XdmValue * node = (PHP_XdmValue*)params[0].implementation();
		if(node != NULL) {
 			xsltProcessor->setSourceFromXdmValue((XdmItem *)node->getInternal());
		}

    	}

}

void  PHP_XsltProcessor::setParameter(Php::Parameters &params){
	PHP_XdmValue * value;
	const char * name;	
	if (params.size()== 2) {
		name = params[0];
		value = (PHP_XdmValue *)params[1].implementation();
		if(name != NULL && value != NULL) {
			xsltProcessor->setParameter(name, value->getInternal());
		}	
	}
}

void  PHP_XsltProcessor::setProperty(Php::Parameters &params){
	if (params.size()== 2) {

		const char * name = params[0];
		const char * value = params[1];
		if(name != NULL && value != NULL) {
 			xsltProcessor->setProperty(name, value);
		}

   	 }
}




void  PHP_XsltProcessor::clearParameters(){
 	xsltProcessor->clearParameters(true);
}

void PHP_XsltProcessor::clearProperties(){		
 	xsltProcessor->clearProperties();
}
void  PHP_XsltProcessor::exceptionClear(){
	xsltProcessor->exceptionClear();
}
Php::Value  PHP_XsltProcessor::exceptionOccurred(){
	bool result = xsltProcessor->exceptionOccurred();
	return result;
}
Php::Value  PHP_XsltProcessor::getErrorCode(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xsltProcessor->getErrorCode(index);
	}
	return NULL;
}

Php::Value  PHP_XsltProcessor::getErrorMessage(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xsltProcessor->getErrorMessage(index);
	}
	return NULL;
}

Php::Value  PHP_XsltProcessor::getExceptionCount(){
	return xsltProcessor->exceptionCount();
}


/*     ============== XQuery10/30/31: PHP Interface of   XQueryProcessor =============== */
    void  PHP_XQueryProcessor::setQueryContent(Php::Parameters &params){
	if (params.size()== 1) {
		const char * queryStr = params[0];
		if(queryStr != NULL)
			xqueryProcessor->setProperty("qs",queryStr);
	}
	
	}
    void  PHP_XQueryProcessor::setContextItem(Php::Parameters &params){
		//TODO allow XdmNode, XdmItem and XdmValue - might not need to do this
	if (params.size()== 1) {
		
		PHP_XdmValue * value = (PHP_XdmValue *)params[0].implementation();
		xqueryProcessor->setContextItem((XdmItem *)(value->getInternal()));
		//value.instanceOf("Xdm");
		
	}

     }
    void  PHP_XQueryProcessor::setContextItemFromFile(Php::Parameters &params){
	const char * filename;
	if (params.size()== 1) {
		filename = params[0];
		if(filename != NULL)
			xqueryProcessor->setContextItemFromFile(filename);	
	}
	
    }

    void  PHP_XQueryProcessor::setParameter(Php::Parameters &params){
	PHP_XdmValue * value;
	const char * name;	
	if (params.size()== 2) {
		name = params[0];
		value = (PHP_XdmValue *)params[1].implementation();
		if(name != NULL && value != NULL) {
			xqueryProcessor->setParameter(name, value->getInternal());
		}	
	}
    }

    void  PHP_XQueryProcessor::setProperty(Php::Parameters &params){
	if (params.size()== 2) {

		const char * name = params[0];
		const char * value = params[1];
		if(name != NULL && value != NULL) {
 			xqueryProcessor->setProperty(name, value);
		}

   	 }
    }

    void  PHP_XQueryProcessor::setOutputFile(Php::Parameters &params){
	if (params.size()== 1) {

		const char * outfilename = params[0];
		if(outfilename != NULL) {
 			xqueryProcessor->setOutputFile(outfilename);
		}

   	 }
   }
    void  PHP_XQueryProcessor::setQueryFile(Php::Parameters &params){
	if (params.size()== 1) {

		const char * outfilename = params[0];
		if(outfilename != NULL) {
 			xqueryProcessor->setQueryFile(outfilename);
		}

   	 }
}
    void  PHP_XQueryProcessor::setQueryBaseURI(Php::Parameters &params){
	if (params.size()== 1) {

		const char * base = params[0];
		if(base != NULL) {
 			xqueryProcessor->setQueryBaseURI(base);
		}

   	 }
}

    void  PHP_XQueryProcessor::declareNamespace(Php::Parameters &params){
	if (params.size()== 2) {

		const char * prefix = params[0];
		const char * ns = params[1];
 		xqueryProcessor->declareNamespace(prefix, ns);
		
   	 }
    }

    void  PHP_XQueryProcessor::clearParameters(){
	 	xqueryProcessor->clearParameters(true);
    }


    void  PHP_XQueryProcessor::clearProperties(){
	xqueryProcessor->clearProperties();
	}

    Php::Value  PHP_XQueryProcessor::runQueryToValue(){
	XdmValue * node = xqueryProcessor->runQueryToValue();
	if(node != NULL) {
		node->incrementRefCount();
		PHP_XdmValue * php_xdmValue = new PHP_XdmValue(node);     
		return Php::Object("Saxon\\XdmValue", php_xdmValue);
        }
	return NULL;
    }
    Php::Value  PHP_XQueryProcessor::runQueryToString(){
	 const char * result = xqueryProcessor->runQueryToString();
        if(result != NULL) {
		return result;
	}
    }

    Php::Value  PHP_XQueryProcessor::runQueryToFile(Php::Parameters &params){
	const char * ofilename;	
	if (params.size()== 1) {

		ofilename = params[0];

		if(ofilename != NULL) {
			xqueryProcessor->setOutputFile(ofilename);	
		}
		
	}
	xqueryProcessor->runQueryToFile();
    }


    void  PHP_XQueryProcessor::exceptionClear(){
	xqueryProcessor->exceptionClear();
}
    Php::Value  PHP_XQueryProcessor::exceptionOccurred(){
	 bool result = xqueryProcessor->exceptionOccurred();
	return result;
}
    Php::Value  PHP_XQueryProcessor::getErrorCode(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xqueryProcessor->getErrorCode(index);
	}
	return NULL;
}

    Php::Value  PHP_XQueryProcessor::getErrorMessage(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xqueryProcessor->getErrorMessage(index);
	}
	return NULL;
}

    Php::Value  PHP_XQueryProcessor::getExceptionCount(){
	int count = xqueryProcessor->exceptionCount();
	return count;
	}


/* ====================== Schema Validator Processor ======================     */


	void PHP_SchemaValidator::setOutputFile(Php::Parameters &params){
		if (params.size()== 1) {
			const char * filename = params[0];
			if(filename != NULL) {
				schemaValidator->setOutputFile(filename);
			}	
			
		}
	}

	void PHP_SchemaValidator::setSourceNode(Php::Parameters &params){
		//TODO check parameter is an XdmNode
		if (params.size()== 1) {
			PHP_XdmNode * node = (PHP_XdmNode *)params[0].implementation();
			if(node != NULL) {
				schemaValidator->setSourceNode((XdmNode *)node->getInternal());
			}	
			
		}

	}

	void PHP_SchemaValidator::registerSchemaFromFile(Php::Parameters &params){
		if (params.size()== 1) {
			const char * name = params[0];
			schemaValidator->registerSchemaFromFile(name);
		}	
	}

	void PHP_SchemaValidator::registerSchemaFromString(Php::Parameters &params){
		if (params.size()== 1) {
			const char * schemaStr = params[0];
			schemaValidator->registerSchemaFromString(schemaStr);
		}
	}

	void PHP_SchemaValidator::validate(Php::Parameters &params){
		if (params.size()== 0) {
			schemaValidator->validate();
		} else if (params.size()== 1) {
			const char * name = params[0];
			schemaValidator->validate(name);
		}
	}


	Php::Value PHP_SchemaValidator::validateToNode(Php::Parameters &params){
		if (params.size()== 1) {
			const char * name = params[0];
			XdmNode * node = schemaValidator->validateToNode(name);
			PHP_XdmNode * php_xdmNode = new PHP_XdmNode(node);     
			return Php::Object("Saxon\\XdmValue", php_xdmNode);
		}
		return NULL;
	}	

	Php::Value PHP_SchemaValidator::getValidationReport(){
		XdmNode * node = schemaValidator->getValidationReport();
		if(node == NULL) {
			std::cerr<<"checkpoint -- node in getValidatorReport is NULL"<<std::endl;
			return NULL;
		}
		PHP_XdmNode * php_xdmNode = new PHP_XdmNode(node);     
		return Php::Object("Saxon\\XdmNode", php_xdmNode);
	}

    	void  PHP_SchemaValidator::setParameter(Php::Parameters &params){
		PHP_XdmValue * value;
		const char * name;	
		if (params.size()== 2) {
			name = params[0];
			value = (PHP_XdmValue *)params[1].implementation();
			if(name != NULL && value != NULL) {
				schemaValidator->setParameter(name, value->getInternal());
			}	
		}
	}

    	void  PHP_SchemaValidator::setProperty(Php::Parameters &params){
		if (params.size()== 2) {

			const char * name = params[0];
			const char * value = params[1];
			if(name != NULL && value != NULL) {
 				schemaValidator->setProperty(name, value);
			}

   	 	}
	}

    	void  PHP_SchemaValidator::clearParameters(){
		schemaValidator->clearParameters(true);
	}

    	void  PHP_SchemaValidator::clearProperties(){
		schemaValidator->clearProperties();
	}

    void  PHP_SchemaValidator::exceptionClear(){
	schemaValidator->exceptionClear();
}
    Php::Value  PHP_SchemaValidator::exceptionOccurred(){
	 bool result = schemaValidator->exceptionOccurred();
	return result;
}
    Php::Value  PHP_SchemaValidator::getErrorCode(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return schemaValidator->getErrorCode(index);
	}
	return NULL;
}

    Php::Value  PHP_SchemaValidator::getErrorMessage(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return schemaValidator->getErrorMessage(index);
	}
	return NULL;
}

    Php::Value  PHP_SchemaValidator::getExceptionCount(){
	int count = schemaValidator->exceptionCount();
	return count;
	}


/* ====================== XPath 2.0/3.0/3.1 Processor ======================     */

    void PHP_XPathProcessor::setContextItem(Php::Parameters &params){
	if (params.size()== 1) {
		XdmValue * value = ((PHP_XdmValue *)params[0].implementation())->getInternal();
		xpathProcessor->setContextItem((XdmItem *)value);
	}
    }


    void PHP_XPathProcessor::setContextFile(Php::Parameters &params){
	if (params.size()== 1) {
		const char * value = params[0];
		xpathProcessor->setContextFile(value);
	}

    }


    void PHP_XPathProcessor::setBaseURI(Php::Parameters &params){
	if (params.size()== 1) {
		const char * value = params[0];
		xpathProcessor->setBaseURI(value);
	}

    }


    Php::Value PHP_XPathProcessor::effectiveBooleanValue(Php::Parameters &params){
	if (params.size()== 1) {
		const char * query = params[0];
		return  xpathProcessor->effectiveBooleanValue(query);
	}
    }


    Php::Value PHP_XPathProcessor::evaluate(Php::Parameters &params){

	if (params.size()== 1) {
		const char * query = params[0];
		XdmValue* value = xpathProcessor->evaluate(query);
	
		PHP_XdmValue * php_xdmValue = new PHP_XdmValue(value);     
		return Php::Object("Saxon\\XdmValue", php_xdmValue);
	}
    }

    Php::Value PHP_XPathProcessor::evaluateSingle(Php::Parameters &params){

	if (params.size()== 1) {
		const char * query = params[0];
		XdmItem* value = xpathProcessor->evaluateSingle(query);
	
		PHP_XdmItem * php_xdmItem = new PHP_XdmItem(value);     
		return Php::Object("Saxon\\XdmItem", php_xdmItem);
	}

    }

    void PHP_XPathProcessor::declareNamespace(Php::Parameters &params){
	if (params.size()== 2) {
		const char * prefix = params[0];
		const char * namespacei = params[1];
		xpathProcessor->declareNamespace(prefix, namespacei);
	}
    }


    void  PHP_XPathProcessor::setParameter(Php::Parameters &params){
	PHP_XdmValue * value;
	const char * name;	
	if (params.size()== 2) {
		name = params[0];
		value = (PHP_XdmValue *)params[1].implementation();
		if(name != NULL && value != NULL) {
			xpathProcessor->setParameter(name, value->getInternal());
		}	
	}
    }


    void  PHP_XPathProcessor::setProperty(Php::Parameters &params){
	if (params.size()== 2) {

		const char * name = params[0];
		const char * value = params[1];
		if(name != NULL && value != NULL) {
 			xpathProcessor->setProperty(name, value);
		}

   	 }
    }


    void  PHP_XPathProcessor::clearParameters(){
	xpathProcessor->clearParameters(true);
    }

    void  PHP_XPathProcessor::clearProperties(){
	xpathProcessor->clearProperties();
    }



void  PHP_XPathProcessor::exceptionClear(){
	xpathProcessor->exceptionClear();
}
    Php::Value  PHP_XPathProcessor::exceptionOccurred(){
	 bool result = xpathProcessor->exceptionOccurred();
	return result;
}
    Php::Value  PHP_XPathProcessor::getErrorCode(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xpathProcessor->getErrorCode(index);
	}
	return NULL;
}

    Php::Value  PHP_XPathProcessor::getErrorMessage(Php::Parameters &params){
	if (params.size()== 1) {
		int index = params[0];
		return xpathProcessor->getErrorMessage(index);
	}
	return NULL;
}

    Php::Value  PHP_XPathProcessor::getExceptionCount(){
	int count = xpathProcessor->exceptionCount();
	return count;
	}



/* ====================== XdmValue ======================     */

	Php::Value PHP_XdmValue::getHead(){
		XdmItem * item = _value->getHead();
		PHP_XdmItem * php_xdmItem = new PHP_XdmItem(item);
		item->incrementRefCount(); 
		return Php::Object("Saxon\\XdmItem", php_xdmItem);
		
	}

	Php::Value PHP_XdmValue::itemAt(Php::Parameters &params){
		if (params.size()== 1) {
			int indexi = params[0];
			XdmItem * item = _value->itemAt(indexi);
			PHP_XdmItem * php_xdmItem = new PHP_XdmItem(item);
			item->incrementRefCount(); 
			return Php::Object("Saxon\\XdmItem", php_xdmItem);
		}
		return NULL;	

	}

	Php::Value PHP_XdmValue::size(){
		return _value->size();
	}

	void PHP_XdmValue::addXdmItem(Php::Parameters &params){
		if (params.size()== 1) {
			PHP_XdmItem * value = (PHP_XdmItem *)params[0].implementation();
			if(value != NULL) {
				_value->addXdmItem((XdmItem *)value->getInternal());
			}
			
		}
	}



/* ======================= XdmItem ====================     */



	Php::Value PHP_XdmItem::getAtomicValue(){
		if(_value != NULL && ((XdmItem *)_value)->isAtomic()){
			PHP_XdmAtomicValue * php_atomicValue = new PHP_XdmAtomicValue((XdmAtomicValue*)_value);
			_value->incrementRefCount(); 
			return Php::Object("Saxon\\XdmAtomicValue", php_atomicValue);
		} else {
			return NULL;
		}
	}

	Php::Value PHP_XdmItem::getNodeValue(){
		if(_value != NULL && ((XdmItem *)_value)->getType() == XDM_NODE){
			PHP_XdmNode * php_node = new PHP_XdmNode((XdmNode *)_value);
			_value->incrementRefCount(); 
			return Php::Object("Saxon\\XdmNode", php_node);
		} else {
			return NULL;
		}
	}


/* ====================== XdmNode ======================     */



	Php::Value PHP_XdmNode::getNodeName(){
		return ((XdmNode *)_value)->getNodeName();
	}

	Php::Value PHP_XdmNode::getNodeKind(){
		return ((XdmNode *)_value)->getNodeKind();
	}



	Php::Value PHP_XdmNode::getChildCount(){
		return ((XdmNode*)_value)->getChildCount();

	}

	Php::Value PHP_XdmNode::getAttributeCount(){
		return ((XdmNode*)_value)->getAttributeCount();
	}

	Php::Value PHP_XdmNode::getChildNode(Php::Parameters &params){
		if (params.size()== 1) {
			int indexi = params[0];
			XdmNode * child = ((XdmNode*)_value)->getChildren()[indexi];
			PHP_XdmNode * php_xdmNode = new PHP_XdmNode(child);
			child->incrementRefCount(); 
			return Php::Object("Saxon\\XdmNode", php_xdmNode);
		}
		return NULL;
	}


	Php::Value PHP_XdmNode::getChildren(){
		XdmNode * node = (XdmNode *)_value;
		XdmNode ** childNodes = node->getChildren();
		Php::Value children;
		int childCount = node->getChildCount();

		for(int i =0; i<childCount;i++){
			PHP_XdmNode * php_xdmNode = new PHP_XdmNode(childNodes[i]);
			childNodes[i]->incrementRefCount(); 
			children[i] = Php::Object("Saxon\\XdmNode", php_xdmNode);

		}
		return children;
	}

	Php::Value PHP_XdmNode::getParent(){
		XdmNode * parent = ((XdmNode*)_value)->getParent();
		PHP_XdmNode * php_xdmNode = new PHP_XdmNode(parent);
		parent->incrementRefCount(); 
		return Php::Object("Saxon\\XdmNode", php_xdmNode);
	}

	Php::Value PHP_XdmNode::getAttributeNode(Php::Parameters &params){
		if (params.size()== 1) {
			int indexi = params[0];
			XdmNode * attNode = ((XdmNode*)_value)->getAttributeNodes()[indexi];
			PHP_XdmNode * php_attNode = new PHP_XdmNode(attNode);
			attNode->incrementRefCount(); 
			return Php::Object("Saxon\\XdmNode", php_attNode);
		}
		return NULL;

	}

	Php::Value PHP_XdmNode::getAttributeNodes(){
		XdmNode * node = (XdmNode *)_value;
		XdmNode ** attrNodes = node->getChildren();
		Php::Value attributes;
		int attrCount = node->getAttributeCount();

		for(int i =0; i<attrCount;i++){
			PHP_XdmNode * php_xdmNode = new PHP_XdmNode(attrNodes[i]);
			attrNodes[i]->incrementRefCount(); 
			attributes[i] = Php::Object("Saxon\\XdmNode", php_xdmNode);

		}
		return attributes;

	}

	Php::Value PHP_XdmNode::getAttributeValue(Php::Parameters &params){

		if (params.size()== 1) {
			const char * name = params[0];
			return  ((XdmNode *)_value)->getAttributeValue(name);
			
		}
		return NULL;
	}


	Php::Value PHP_XdmAtomicValue::getStringValue(){

		return ((XdmAtomicValue*)_value)->getStringValue();
	}

	Php::Value PHP_XdmAtomicValue::getLongValue(){

		return (int)((XdmAtomicValue*)_value)->getLongValue();
	}

	Php::Value PHP_XdmAtomicValue::getBooleanValue(){

		return ((XdmAtomicValue*)_value)->getBooleanValue();
	}

	Php::Value PHP_XdmAtomicValue::getDoubleValue(){

		return ((XdmAtomicValue*)_value)->getDoubleValue();
	}

	Php::Value PHP_XdmAtomicValue::isAtomic(){

		return true;
	}


	
/**
 *  tell the compiler that the get_module is a pure C function
 */
extern "C" {
    
    /**
     *  Function that is called by PHP right after the PHP process
     *  has started, and that returns an address of an internal PHP
     *  strucure with all the details and features of your extension
     *
     *  @return void*   a pointer to an address that is understood by PHP
     */
    PHPCPP_EXPORT void *get_module() 
    {
        // static(!) Php::Extension object that should stay in memory
        // for the entire duration of the process (that's why it's static)
        static Php::Extension extension(PHP_SAXON_EXTNAME, PHP_SAXON_EXTVER);
	 // description of the class so that PHP knows which methods are accessible
        Php::Class<PHP_SaxonProcessor> saxonProcessor("Saxon\\SaxonProcessor");
	saxonProcessor.method<&PHP_SaxonProcessor::__construct> ("__construct");
        saxonProcessor.method<&PHP_SaxonProcessor::createAtomicValue> ("createAtomicValue");
        saxonProcessor.method<&PHP_SaxonProcessor::parseXmlFromString> ("parseXmlFromString");
        saxonProcessor.method<&PHP_SaxonProcessor::parseXmlFromFile>     ("parseXmlFromFile");
        saxonProcessor.method<&PHP_SaxonProcessor::setcwd>     ("setcwd");
        saxonProcessor.method<&PHP_SaxonProcessor::setResourcesDirectory>     ("setResourcesDirectory");
        saxonProcessor.method<&PHP_SaxonProcessor::setConfigurationProperty>     ("setConfigurationProperty");
        saxonProcessor.method<&PHP_SaxonProcessor::newXsltProcessor>     ("newXsltProcessor");
        saxonProcessor.method<&PHP_SaxonProcessor::newXQueryProcessor>     ("newXQueryProcessor");
	saxonProcessor.method<&PHP_SaxonProcessor::newXPathProcessor>     ("newXPathProcessor");
	saxonProcessor.method<&PHP_SaxonProcessor::newSchemaValidator>     ("newSchemaValidator");
	saxonProcessor.method<&PHP_SaxonProcessor::registerPHPFunction>     ("registerPHPFunction");
	saxonProcessor.method<&PHP_SaxonProcessor::version>     ("version");

	Php::Class<PHP_XdmValue> xdmValue("Saxon\\XdmValue");
	//saxonProcessor.method<&PHP_SaxonProcessor::__construct> ("__construct");
	xdmValue.method<&PHP_XdmValue::itemAt> ("itemAt");
	xdmValue.method<&PHP_XdmValue::getHead> ("getHead");
	xdmValue.method<&PHP_XdmValue::size> ("size");
	xdmValue.method<&PHP_XdmValue::addXdmItem> ("addXdmItem");



/*

Php::Value getHead();
	Php::Value itemAt(Php::Parameters &params);
	Php::Value size();
	Php::Value addXdmItem(Php::Parameters &params);
*/

	Php::Class<PHP_XdmItem> xdmItem("Saxon\\XdmItem");
	xdmItem.method<&PHP_XdmItem::isAtomic> ("isAtomic");
	xdmItem.method<&PHP_XdmItem::isNode> ("isNode");
	xdmItem.method<&PHP_XdmItem::getAtomicValue> ("getAtomicValue");
	xdmItem.method<&PHP_XdmItem::getNodeValue> ("getNodeValue");
	xdmItem.method<&PHP_XdmItem::getStringValue> ("getStringValue");

	Php::Class<PHP_XdmNode> xdmNode("Saxon\\XdmNode");
	xdmNode.method<&PHP_XdmNode::getStringValue> ("getStringValue");
	xdmNode.method<&PHP_XdmNode::isAtomic> ("isAtomic");
	xdmNode.method<&PHP_XdmNode::isNode> ("isNode");
	xdmNode.method<&PHP_XdmNode::getNodeName> ("getNodeName");
	xdmNode.method<&PHP_XdmNode::getNodeKind> ("getNodeKind");
	xdmNode.method<&PHP_XdmNode::getChildCount> ("getChildCount");
	xdmNode.method<&PHP_XdmNode::getAttributeCount> ("getAttributeCount");
	xdmNode.method<&PHP_XdmNode::getChildren> ("getChildren");
	xdmNode.method<&PHP_XdmNode::getChildNode> ("getChildNode");
	xdmNode.method<&PHP_XdmNode::getParent> ("getParent");
	xdmNode.method<&PHP_XdmNode::getAttributeNode> ("getAttributeNode");
	xdmNode.method<&PHP_XdmNode::getAttributeNodes> ("getAttributeNodes");
	xdmNode.method<&PHP_XdmNode::getAttributeValue> ("getAttributeValue");




	Php::Class<PHP_XdmAtomicValue> xdmAtomicValue("Saxon\\XdmAtomicValue");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::getStringValue> ("getStringValue");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::getBooleanValue> ("getBooleanValue");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::getLongValue> ("getLongValue");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::getDoubleValue> ("getDoubleValue");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::isAtomic> ("isAtomic");
	xdmAtomicValue.method<&PHP_XdmAtomicValue::isNode> ("isNode");
	

	xdmItem.extends(xdmValue);
	xdmNode.extends(xdmValue);
	xdmAtomicValue.extends(xdmValue);

	Php::Class<PHP_XsltProcessor> xsltProcessor("Saxon\\XsltProcessor");
	xsltProcessor.method<&PHP_XsltProcessor::transformFileToFile> ("transformFileToFile");
	xsltProcessor.method<&PHP_XsltProcessor::transformFileToString> ("transformFileToString");
	xsltProcessor.method<&PHP_XsltProcessor::transformToString> ("transformToString");
	xsltProcessor.method<&PHP_XsltProcessor::transformToValue> ("transformToValue");
	xsltProcessor.method<&PHP_XsltProcessor::transformToFile> ("transformToFile");
	xsltProcessor.method<&PHP_XsltProcessor::compileFromFile> ("compileFromFile");
	xsltProcessor.method<&PHP_XsltProcessor::compileFromValue> ("compileFromValue");
	xsltProcessor.method<&PHP_XsltProcessor::compileFromString> ("compileFromString");
	xsltProcessor.method<&PHP_XsltProcessor::setOutputFile> ("setOutputFile");
	xsltProcessor.method<&PHP_XsltProcessor::setSourceFromFile> ("setSourceFromFile");
	xsltProcessor.method<&PHP_XsltProcessor::setSourceFromXdmValue> ("setSourceFromXdmValue");
	xsltProcessor.method<&PHP_XsltProcessor::setParameter> ("setParameter");
	xsltProcessor.method<&PHP_XsltProcessor::setProperty> ("setProperty");
	xsltProcessor.method<&PHP_XsltProcessor::clearParameters> ("clearParameters");
	xsltProcessor.method<&PHP_XsltProcessor::exceptionClear> ("exceptionClear");
	xsltProcessor.method<&PHP_XsltProcessor::exceptionOccurred> ("exceptionOccurred");
	xsltProcessor.method<&PHP_XsltProcessor::clearProperties> ("clearProperties");
    	xsltProcessor.method<&PHP_XsltProcessor::getErrorCode>("getErrorCode");
	xsltProcessor.method<&PHP_XsltProcessor::getErrorMessage> ("getErrorMessage");
	xsltProcessor.method<&PHP_XsltProcessor::getExceptionCount> ("getExceptionCount");

	Php::Class<PHP_XQueryProcessor> xqueryProcessor("Saxon\\XQueryProcessor");

    xqueryProcessor.method<&PHP_XQueryProcessor::setQueryContent>("setQueryContent");
    xqueryProcessor.method<&PHP_XQueryProcessor::setContextItem>("setContextItem");
    xqueryProcessor.method<&PHP_XQueryProcessor::setContextItemFromFile>("setContextItemFromFile");
    xqueryProcessor.method<&PHP_XQueryProcessor::setParameter>("setParameter");
    xqueryProcessor.method<&PHP_XQueryProcessor::setProperty>("setProperty");
    xqueryProcessor.method<&PHP_XQueryProcessor::setOutputFile>("setOutputFile");
    xqueryProcessor.method<&PHP_XQueryProcessor::setQueryFile>("setQueryFile");
    xqueryProcessor.method<&PHP_XQueryProcessor::setQueryBaseURI>("setQueryBaseURI");
    xqueryProcessor.method<&PHP_XQueryProcessor::declareNamespace>("declareNamespace");
    xqueryProcessor.method<&PHP_XQueryProcessor::clearParameters>("clearParameters");
    xqueryProcessor.method<&PHP_XQueryProcessor::clearProperties>("clearProperties");
    xqueryProcessor.method<&PHP_XQueryProcessor::runQueryToValue>("runQueryToValue");
    xqueryProcessor.method<&PHP_XQueryProcessor::runQueryToString>("runQueryToString");
    xqueryProcessor.method<&PHP_XQueryProcessor::runQueryToFile>("runQueryToFile");
    xqueryProcessor.method<&PHP_XQueryProcessor::exceptionClear>("exceptionClear");
    xqueryProcessor.method<&PHP_XQueryProcessor::exceptionOccurred>("exceptionOccurred");
    xqueryProcessor.method<&PHP_XQueryProcessor::getErrorCode>("getErrorCode");
    xqueryProcessor.method<&PHP_XQueryProcessor::getErrorMessage>("getErrorMessage");
    xqueryProcessor.method<&PHP_XQueryProcessor::getExceptionCount>("getExceptionCount");

	Php::Class<PHP_XPathProcessor> xpathProcessor("Saxon\\XPathProcessor");
	
	xpathProcessor.method<&PHP_XPathProcessor::setContextItem>("setContextItem");
	xpathProcessor.method<&PHP_XPathProcessor::setContextFile>("setContextFile");
	xpathProcessor.method<&PHP_XPathProcessor::setBaseURI>("setBaseURI");
	xpathProcessor.method<&PHP_XPathProcessor::effectiveBooleanValue>("effectiveBooleanValue");
	xpathProcessor.method<&PHP_XPathProcessor::evaluate>("evaluate");
	xpathProcessor.method<&PHP_XPathProcessor::evaluateSingle>("evaluateSingle");
	xpathProcessor.method<&PHP_XPathProcessor::declareNamespace>("declareNamespace");
	xpathProcessor.method<&PHP_XPathProcessor::setParameter>("setParameter");
	xpathProcessor.method<&PHP_XPathProcessor::setProperty>("setProperty");
	xpathProcessor.method<&PHP_XPathProcessor::clearParameters>("clearParameters");
	xpathProcessor.method<&PHP_XPathProcessor::clearProperties>("clearProperties");
	xpathProcessor.method<&PHP_XPathProcessor::exceptionClear>("exceptionClear");
	xpathProcessor.method<&PHP_XPathProcessor::exceptionOccurred>("exceptionOccurred");
	xpathProcessor.method<&PHP_XPathProcessor::getErrorCode>("getErrorCode");
	xpathProcessor.method<&PHP_XPathProcessor::getErrorMessage>("getErrorMessage");
	xpathProcessor.method<&PHP_XPathProcessor::getExceptionCount>("getExceptionCount");


	Php::Class<PHP_SchemaValidator> schemaValidator("Saxon\\SchemaValidator");
	schemaValidator.method<&PHP_SchemaValidator::setSourceNode>("setSourceNode");
	schemaValidator.method<&PHP_SchemaValidator::setOutputFile>("setOutputFile");
	schemaValidator.method<&PHP_SchemaValidator::registerSchemaFromFile>("registerSchemaFromFile");
	schemaValidator.method<&PHP_SchemaValidator::registerSchemaFromString>("registerSchemaFromString");
	schemaValidator.method<&PHP_SchemaValidator::validate>("validate");
	schemaValidator.method<&PHP_SchemaValidator::validateToNode>("validateToNode");
	schemaValidator.method<&PHP_SchemaValidator::getValidationReport>("getValidationReport");
	schemaValidator.method<&PHP_SchemaValidator::setParameter>("setParameter");
	schemaValidator.method<&PHP_SchemaValidator::setProperty>("setProperty");
	schemaValidator.method<&PHP_SchemaValidator::clearParameters>("clearParameters");
	schemaValidator.method<&PHP_SchemaValidator::clearProperties>("clearProperties");
	schemaValidator.method<&PHP_SchemaValidator::exceptionClear>("exceptionClear");
	schemaValidator.method<&PHP_SchemaValidator::exceptionOccurred>("exceptionOccurred");
	schemaValidator.method<&PHP_SchemaValidator::getErrorCode>("getErrorCode");
	schemaValidator.method<&PHP_SchemaValidator::getErrorMessage>("getErrorMessage");
	schemaValidator.method<&PHP_SchemaValidator::getExceptionCount>("getExceptionCount");

        // add the class to the extension
        extension.add(std::move(saxonProcessor));
        extension.add(std::move(xdmValue));
        extension.add(std::move(xdmItem));
        extension.add(std::move(xdmNode));
        extension.add(std::move(xdmAtomicValue));
        extension.add(std::move(xsltProcessor));
	extension.add(std::move(xqueryProcessor));
	extension.add(std::move(xpathProcessor));
	extension.add(std::move(schemaValidator));
        
        // return the extension
        return extension;
    }
}
        

