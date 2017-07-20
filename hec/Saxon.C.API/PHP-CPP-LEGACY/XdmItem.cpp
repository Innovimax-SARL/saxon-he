

#include "XdmItem.h"

XdmItem::XdmItem(): XdmValue(){
	value = NULL;
}

    XdmItem::XdmItem(const XdmItem &other){
	value = (sxnc_value *)malloc(sizeof(sxnc_value));
        value->xdmvalue = other.value->xdmvalue;
	xdmSize =1;
	refCount = other.refCount;
    }


XdmItem::XdmItem(jobject obj){
	value = (sxnc_value *)malloc(sizeof(sxnc_value));
        value->xdmvalue = obj;
	xdmSize =1;
	refCount =1;
}

bool XdmItem::isAtomic(){
	return false;
}




   XdmItem * XdmItem::getHead(){ return this;}

  XdmItem * XdmItem::itemAt(int n){
	if (n < 0 || n >= size()) {
		return NULL;	
	}
	return this;
  }



 int XdmItem::size(){
	return 1;	
   }

jobject XdmItem::getUnderlyingValue(SaxonProcessor * proc){
#ifdef DEBUG
	std::cerr<<std::endl<<"XdmItem-getUnderlyingValue:"<<std::endl; 
#endif 
	if(value == NULL) {
#ifdef DEBUG
	std::cerr<<std::endl<<"XdmItem-getUnderlyingValue-NULL RETURNED:"<<std::endl; 
#endif
		return NULL;	
	}
	return value->xdmvalue;
}

    const char * XdmItem::getStringValue(SaxonProcessor * proc1){
	if(proc != NULL && proc1 != NULL) {	
		proc = proc1;
	}
	if(proc != NULL) {
		return proc->getStringValue(this);
	} else {
		return "";
	}
   }

	/**
	* Get the type of the object
	*/
	XDM_TYPE XdmItem::getType(){
		return XDM_ITEM;
	}
