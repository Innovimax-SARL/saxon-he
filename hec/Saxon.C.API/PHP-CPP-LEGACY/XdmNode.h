////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

#ifndef SAXON_XDMNODE_h
#define SAXON_XDMNODE_h

#include "XdmItem.h"
#include <string.h>
//#include "XdmValue.h"

typedef enum eXdmNodeKind { DOCUMENT=9, ELEMENT=1, ATTRIBUTE=2, TEXT=3, COMMENT=8, PROCESSING_INSTRUCTION=7, NAMESPACE=13, UNKNOWN=0 } XDM_NODE_KIND;



class XdmValue;

class XdmNode : public XdmItem
{

public:


   /* XdmNode(const XdmValue& valuei){
	//value = (sxnc_value *)malloc(sizeof(sxnc_value));
        value = valuei.values[0]->getUnderlyingCValue();
	xdmSize =1;
	refCount = 1;
	nodeKind = UNKNOWN;
	}*/

    XdmNode(jobject);

    XdmNode(XdmNode *parent, jobject, XDM_NODE_KIND);

     virtual ~XdmNode(){
	delete baseURI;
	delete nodeName;

	//There might be potential issues with children and attribute node not being deleted when the parent node has been deleted
	//we need to track this kind of problem.
    }

    virtual bool isAtomic();
    
    
    XDM_NODE_KIND getNodeKind();

    /**
     * Get the name of the node, as a string in the form of a EQName
     *
     * @return the name of the node. In the case of unnamed nodes (for example, text and comment nodes)
     *         return null.
     */
    const char * getNodeName();
    
    const char* getBaseUri();
    
    
    XdmValue * getTypedValue();
    
    XdmNode* getParent();

    
    const char* getAttributeValue(const char *str);
    
    int getAttributeCount();

    XdmNode** getAttributeNodes();

    
    XdmNode** getChildren();

    int getChildCount();

     /**
	* Get the type of the object
	*/
    XDM_TYPE getType(){
	return XDM_NODE;
    }
    
   // const char* getOuterXml();


    
private:
	const char * baseURI;
	const char * nodeName;
	XdmNode ** children; //caches child nodes when getChildren method is first called;
	int childCount;
	XdmNode * parent;
	XdmNode ** attrValues;//caches attribute nodes when getAttributeNodes method is first called;
	int attrCount;
	XDM_NODE_KIND nodeKind;
    
};

#endif
