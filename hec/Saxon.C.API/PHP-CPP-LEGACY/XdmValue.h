////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2014 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

#ifndef SAXON_XDMVALUE_H
#define SAXON_XDMVALUE_H


#include <string.h>
#include "SaxonProcessor.h"
#include <typeinfo> //used for testing only
#include <vector>
#include <deque>
#include <list>
#include "SaxonCGlue.h"
#include "SaxonCXPath.h"


/*! <code>XdmValue</code>. Value in the XDM data model. A value is a sequence of zero or more items,
 * each item being either an atomic value or a node. This class is a wrapper of the the XdmValue object created in Java.
 * <p/>
 */

class SaxonProcessor;
class XdmItem;


typedef enum eXdmType { XDM_VALUE=1, XDM_ITEM=2, XDM_NODE=3, XDM_ATOMIC_VALUE=4, XDM_FUNCTION_ITEM=5 } XDM_TYPE;

class XdmValue {


public:
/**
     * A default Constructor. Create a empty value
     */
	XdmValue(){
		xdmSize = 0;
		refCount = 1;
		jValues = NULL;
		valueType = NULL;
	}

	XdmValue(SaxonProcessor * p){
		proc = p;
		jValues = NULL;
		refCount = 1;
		valueType = NULL;
	}


    /**
     * A copy constructor.
     * @param val - Xdmvalue
     */
	//XdmValue(const XdmValue &other);

     /**
     * Constructor. Create a value from a collection of items
     * @param val - Xdmvalue
     */
	//XdmValue(XdmValue * items, int length);

     /**
     * Constructor. Create a value from a collection of items
     * @param container - entire container is expected
     */
     template <class ContainerType>
     XdmValue(const ContainerType& container);

     /**
     * Constructor. Create a value from a collection of items
     * @param container - entire container is expected
     */
     template <class ForwardIteratorType> 
     XdmValue(ForwardIteratorType begin, ForwardIteratorType end);
 
     /**
     * Constructor. Create a value from a collection of items
     * @param val - Xdmvalue
     */
	//XdmValue(XdmItem *items, int length);

    /**
     * Add an XdmItem  to the sequence. This method is designed for the primitive types.
     * @param type - specify target type of the value  
     * @param val - Value to convert
     */
	XdmValue * addXdmValueWithType(const char * tStr, const char * val);//TODO check and document

    /**
     * Add an XdmItem to the sequence. 
     * See methods the functions in SaxonCXPath of the C library
     * @param val - XdmItem object
     */
     void addXdmItem(XdmItem *val);

    /**
     * Add an Java XdmValue object to the sequence. 
     * See methods the functions in SaxonCXPath of the C library
     * @param val - Java object
     */
     XdmValue * addUnderlyingValue(jobject val);

  

	//TODO XdmValue with constructor of sequence of values

    /**
     * A Constructor. Wrap an Java XdmValue object.
     * @param val - Java XdmValue object
     */
	XdmValue(jobject val);


	virtual ~XdmValue();

	void releaseXdmValue();


    /**
     * Get the first item in the sequence
     * @return XdmItem or null if sequence is empty
     */
	virtual XdmItem * getHead();

  /**
     * Get the n'th item in the value, counting from zero.
     *
     * @param n the item that is required, counting the first item in the sequence as item zero
     * @return the n'th item in the sequence making up the value, counting from zero
     * return NULL  if n is less than zero or greater than or equal to the number
     *                                    of items in the value
     * return NULL if the value is lazily evaluated and the delayed
     *                                    evaluation fails with a dynamic error.
     */
	virtual XdmItem * itemAt(int n);

    /**
     * Get the number of items in the sequence
     *
     */
      virtual int size();

      int getRefCount(){
	return refCount;
      }


	
      void incrementRefCount(){
	refCount++;
	//std::cerr<<"refCount-inc-xdmVal="<<refCount<<" ob ref="<<(this)<<std::endl;
      }

      void decrementRefCount(){
	if(refCount >0)
		refCount--;
	//std::cerr<<"refCount-dec-xdmVal="<<refCount<<" ob ref="<<(this)<<std::endl;
      }

	void setProcessor(SaxonProcessor *p){proc = p;}

        /*const char * getErrorMessage(int i);
        const char * getErrorCode(int i);
	int exceptionCount();*/
	const char * checkFailures(){return failure;}

   /**
     * Get Java XdmValue object.
     * @return jobject - The Java object of the XdmValue in its JNI representation
     */
	virtual  jobject getUnderlyingValue(SaxonProcessor * proc);

	/**
	* Get the type of the object
	*/
	virtual XDM_TYPE getType();

protected:
	SaxonProcessor *proc;
	char* valueType;  /*!< Cached. The type of the XdmValue */
	//string valueStr;  /*!< Cached. String representation of the XdmValue, if available */

	std::vector<XdmItem*> values;
	int xdmSize; 	/*!< Cached. The count of items in the XdmValue */
	int refCount;
private:
	
	jobjectArray jValues;
};

#endif /** SAXON_XDMVALUE_H **/
