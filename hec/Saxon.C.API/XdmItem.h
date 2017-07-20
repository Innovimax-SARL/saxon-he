////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

#ifndef SAXON_XDMITEM_h
#define SAXON_XDMITEM_h

#include "XdmValue.h"

class SaxonProcessor;

class XdmItem : public XdmValue
{

public:

     XdmItem();

     XdmItem(jobject);

    XdmItem(const XdmItem &item);

	
    virtual ~XdmItem(){
//std::cerr<<std::endl<<"XdmItem destructor called, refCount"<<getRefCount()<<std::endl;
	if(value !=NULL && proc != NULL) {
			SaxonProcessor::sxn_environ->env->DeleteLocalRef(value->xdmvalue);
	}
	free(value);
    }
    
    virtual bool isAtomic();

//TODO: isNode
//TODO: isFunction

    /**
     * Get Java XdmValue object.
     * @return jobject - The Java object of the XdmValue in its JNI representation
     */
     virtual  jobject getUnderlyingValue(SaxonProcessor * proc);

     sxnc_value * getUnderlyingCValue(){
	return value;
     }

     virtual const char * getStringValue(SaxonProcessor * proc=NULL); 

 /**
     * Get the first item in the sequence
     * @return XdmItem or null if sequence is empty
     */
	XdmItem * getHead();

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
	XdmItem * itemAt(int n);

    /**
     * Get the number of items in the sequence
     *
     */
      int size();



	/**
	* Get the type of the object
	*/
	virtual XDM_TYPE getType();

 protected:  
	sxnc_value* value;
};


#endif
