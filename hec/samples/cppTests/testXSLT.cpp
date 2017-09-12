#include <sstream>

#include "../../Saxon.C.API/SaxonProcessor.h"
#include "../../Saxon.C.API/XdmValue.h"
#include "../../Saxon.C.API/XdmItem.h"
#include "../../Saxon.C.API/XdmNode.h"
#include "cppExtensionFunction.cpp"
#include <string>

// TODO: write test case for checking parameters which are null


using namespace std;

JNINativeMethod cppMethods[] =
{
    {
         "_nativeCall",
         "(Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;",
         (void *)&cppNativeCall
    }
};


/*
* Test transform to String. Source and stylesheet supplied as arguments
*/
void testTransformToString1(SaxonProcessor * processor, XsltProcessor * trans){
	
  cout<<endl<<"Test: TransformToString1:"<<endl;

    const char * output = trans->transformFileToString("cat.xml", "test.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
	delete output;

}

/*
* Test transform to String. Source and stylesheet supplied as arguments
*/
void testTransformToStringExtensionFunc(SaxonProcessor * processor, XsltProcessor * trans){
	
  cout<<endl<<"Test: TransformToStringExtensionFunc:"<<endl;
trans->setProperty("extc", "/home/ond1/work/new-svn/latest9.8-hec/hec/samples/cppTests/cppExtensionFunction");
cout<<endl<<"Test: checkpoint 1:"<<endl;
processor->registerNativeMethods(SaxonProcessor::sxn_environ->env, "com/saxonica/functions/extfn/cpp/NativeCall",
    cppMethods, sizeof(cppMethods) / sizeof(cppMethods[0]));
cout<<endl<<"Test: checkpoint 2:"<<endl;
    const char * output = trans->transformFileToString("cat.xml", "testExtension.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
	delete output;

}



/*
* Test transform to String. stylesheet supplied as argument. Source supplied as XdmNode
*/
void testTransformToString2(SaxonProcessor * processor, XsltProcessor * trans){

  cout<<endl<<"Test: TransformToString2:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromFile("cat.xml");

   if(input == NULL) {
	cout<<"Source document is null."<<endl;

    }

     trans->setSourceFromXdmValue((XdmItem*)input);
    const char * output = trans->transformFileToString(NULL, "test.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;
	
	

}

/*
* Test transform to String. stylesheet supplied as argument. Source supplied as XdmNode
Should be error. Stylesheet file does not exist
*/
void testTransformToString2a(SaxonProcessor * processor, XsltProcessor * trans){

  cout<<endl<<"Test: TransformToString2a:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromFile("cat.xml");

   if(input == NULL) {
	cout<<"Source document is null."<<endl;

    }

     trans->setSourceFromXdmValue((XdmItem*)input);
    const char * output = trans->transformFileToString(NULL, "test-error.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;
	
	

}

/*
* Test transform to String. stylesheet supplied as argument. Source supplied as XdmNode
Should be error. Source file does not exist
*/
void testTransformToString2b(SaxonProcessor * processor, XsltProcessor * trans){

  cout<<endl<<"Test: TransformToString2b:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromFile("cat-error.xml");

   if(input == NULL) {
	cout<<"Source document is null."<<endl;

    }

     trans->setSourceFromXdmValue((XdmItem*)input);
    const char * output = trans->transformFileToString(NULL, "test-error.xsl");
   if(output == NULL) {
      printf("result is null \nCheck For errors:");
      if(trans->exceptionCount()>0) {
	cout<<trans->getErrorMessage(0)<<endl;
      }	
    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;
	
    trans->exceptionClear();

}


/*
* Test transform to String. stylesheet supplied as argument. Source supplied as xml string
and integer parmater created and supplied
*/
void testTransformToString3(SaxonProcessor * processor, XsltProcessor * trans){

  cout<<endl<<"Test: TransformToString3:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * inputi = processor->parseXmlFromString("<out><person>text1</person><person>text2</person><person>text3</person></out>");

   XdmAtomicValue * value1 = processor->makeIntegerValue(10);

   trans->setParameter("numParam",(XdmValue *)value1);

   if(inputi == NULL) {
	cout<<"Source document inputi is null."<<endl;

    }

    trans->setSourceFromXdmValue((XdmItem*)inputi);
    const char * output = trans->transformFileToString(NULL, "test.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
   delete output;

}

/*
* Test transform to String. stylesheet supplied as argument. Source supplied as xml string
and integer parmater created and supplied
*/
void testTransformToString4(SaxonProcessor * processor, XsltProcessor * trans){

  cout<<endl<<"Test: TransformToString4:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromString("<out><person>text1</person><person>text2</person><person>text3</person></out>");

   XdmValue * values = new XdmValue(processor);
   values->addXdmItem((XdmItem*)processor->makeIntegerValue(10));
  values->addXdmItem((XdmItem*)processor->makeIntegerValue(5));
  values->addXdmItem((XdmItem*)processor->makeIntegerValue(6));
  values->addXdmItem((XdmItem*)processor->makeIntegerValue(7));
   
   trans->setParameter("values",(XdmValue *)values);

   if(input == NULL) {
	cout<<"Source document is null."<<endl;

    }

    trans->setSourceFromXdmValue((XdmItem*)input);
    const char * output = trans->transformFileToString(NULL, "test2.xsl");
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;

}

void testTransfromFromstring(SaxonProcessor * processor, XsltProcessor * trans){
cout<<endl<<"Test: testTransfromFromstring:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromString("<out><person>text1</person><person>text2</person><person>text3</person></out>");

   trans->compileFromString("<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'>       <xsl:param name='values' select='(2,3,4)' /><xsl:output method='xml' indent='yes' /><xsl:template match='*'><output><xsl:for-each select='$values' ><out><xsl:value-of select='. * 3'/></out></xsl:for-each></output></xsl:template></xsl:stylesheet>");
trans->setSourceFromXdmValue((XdmItem*)input);
 const char * output = trans->transformToString();
   if(output == NULL) {
      printf("result is null \n");

    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;


}

//Test case has error in the stylesheet
void testTransfromFromstring2Err(SaxonProcessor * processor, XsltProcessor * trans){
cout<<endl<<"Test: testTransfromFromstring2-Error:"<<endl;
  trans->clearParameters(true);
  trans->clearProperties();
    XdmNode * input = processor->parseXmlFromString("<out><person>text1</person><person>text2</person><person>text3</person></out>");

   trans->compileFromString("<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'>       <xsl:param name='values' select='(2,3,4)' /><xsl:output method='xml' indent='yes' /><xsl:template match='*'><output><xsl:for-each select='$values' ><out><xsl:value-of select='. * 3'/></out><xsl:for-each></output></xsl:template><xsl:stylesheet>");
trans->setSourceFromXdmValue((XdmItem*)input);
 const char * output = trans->transformToString();
   if(output == NULL) {
      printf("result is null \nCheck For errors:\n");
      if(trans->exceptionCount()>0) {
	cout<<"Error count="<<trans->exceptionCount()<<", "<<trans->getErrorMessage(0)<<endl;
      }	
    }else {
      printf("%s", output);
      printf("result is OK \n");
    }
      fflush(stdout);
    delete output;

   trans->exceptionClear();


}

void testTrackingOfValueReference(SaxonProcessor * processor, XsltProcessor * trans){
  trans->clearParameters(true);
  trans->clearProperties();
 cout<<endl<<"Test: TrackingOfValueReference:"<<endl;
ostringstream test;
ostringstream valStr;
ostringstream name;
	for(int i=0; i<10; i++){
		test<<"v"<<i;
		valStr<<"<out><person>text1</person><person>text2</person><person>text3</person><value>"<<test.str()<<"</value></out>";
		name<<"value"<<i;
		
		XdmValue * values = (XdmValue*)processor->parseXmlFromString(valStr.str().c_str());
		//cout<<"Name:"<<name.str()<<", Value:"<<values->getHead()->getStringValue(processor)<<endl;
		trans->setParameter(name.str().c_str(), values);
		test.str("");
		valStr.str("");
		name.str("");
	}
	
	std::map<std::string,XdmValue*> parMap = trans->getParameters();
	if(parMap.size()>0) {
cout<<"Parameter size: "<<parMap.size()<<endl;
	cout<<"Parameter size: "<<parMap.size()<<endl;//", Value:"<<trans->getParameters()["value0"]->getHead()->getStringValue(processor)<<endl;
ostringstream name1;
	for(int i =0; i < 10;i++){
		name1<<"param:value"<<i;
		cout<<" i:"<<i<<" Map size:"<<parMap.size()<<", ";
		XdmValue * valuei = parMap[name1.str()];
		if(valuei != NULL ){
			cout<<name1.str();
			if(valuei->itemAt(0) != NULL)
				cout<<"= "<<valuei->itemAt(0)->getStringValue(processor);
			cout<<endl;
		}
		name1.str("");
	}
	}
}

/*Test case should be error.*/
void testTrackingOfValueReferenceError(SaxonProcessor * processor, XsltProcessor * trans){
  trans->clearParameters(true);
  trans->clearProperties();
 cout<<endl<<"Test: TrackingOfValueReference-Error:"<<endl;
cout<<"Parameter Map size: "<<(trans->getParameters().size())<<endl;
ostringstream test;
ostringstream valStr;
ostringstream name;
	for(int i=0; i<10; i++){
		test<<"v"<<i;
		valStr<<"<out><person>text1</person><person>text2</person><person>text3</person><value>"<<test.str()<<"<value></out>";
		name<<"value"<<i;
		
		XdmValue * values = (XdmValue*)processor->parseXmlFromString(valStr.str().c_str());
		trans->setParameter(name.str().c_str(), values);
		test.str("");
		valStr.str("");
		name.str("");
	}
	std::map<std::string,XdmValue*> parMap = trans->getParameters();
cout<<"Parameter Map size: "<<parMap.size()<<endl;
	
ostringstream name1;
	for(int i =0; i < 10;i++){
		name1<<"param:value"<<i;
		cout<<" i:"<<i<<" Map size:"<<parMap.size()<<", ";
		 try {
		XdmValue * valuei = parMap.at(name1.str());
		if(valuei != NULL ){
			cout<<name1.str();
			if(valuei->itemAt(0) != NULL)
				cout<<"= "<<valuei->itemAt(0)->getStringValue(processor);
			cout<<endl;
		}
		} catch(const std::out_of_range& oor) {
			cout<< "Out of range exception occurred. Exception no. "<<endl;	
		}
		name1.str("");
	}
}


void testXdmNodeOutput(XsltProcessor * trans){
 trans->clearParameters(true);
  trans->clearProperties();


  trans->compileFromString("<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template name='go'><a/></xsl:template></xsl:stylesheet>");
	    trans->setProperty("it","go");
            XdmNode * root = (XdmNode*)(trans->transformToValue());
	     if(root == NULL) {  
		             
			cout<<"Result is null"<<endl;
			return;
		} 
               if(root->getNodeKind() == DOCUMENT){
		   cout<<"Result is a Document"<<endl;
		} else {
			cout<<"Node is of kind:"<<root->getNodeKind()<<endl;
		}

  trans->clearProperties();

}

void exampleSimple1(XsltProcessor  *proc){
		cout<<"ExampleSimple1 taken from PHP:"<<endl;
                proc->setSourceFromFile("../php/xml/foo.xml");
                proc->compileFromFile("../php/xsl/foo.xsl");
  	              
                const char *result = proc->transformToString();               
		if(result != NULL) {               
			cout<<result<<endl;
		} else {
			cout<<"Result is null"<<endl;
		}
		proc->clearParameters(true);
		proc->clearProperties();            
            }

void exampleSimple1Err(XsltProcessor  *proc){
		cout<<"ExampleSimple1 taken from PHP:"<<endl;
                proc->setSourceFromFile("cat.xml");
                proc->compileFromFile("err.xsl");
  	              
                const char *result = proc->transformToString();               
		if(result != NULL) {               
			cout<<result<<endl;
		} else {
			cout<<"Result is null"<<endl;
		}
		proc->clearParameters(true);
		proc->clearProperties();            
            }

int exists(const char *fname)
{
    FILE *file;
    if (file = fopen(fname, "r"))
    {
        fclose(file);
        return 1;
    }
    return 0;
}


  void exampleSimple2(XsltProcessor  *proc){
		cout<<"<b>exampleSimple2:</b><br/>"<<endl;
                proc->setSourceFromFile("../php/xml/foo.xml");
                proc->compileFromFile("../php/xsl/foo.xsl");
                const char * filename = "output1.xml";
		proc->setOutputFile(filename);
                proc->transformToFile();
				
		if (exists(filename)) {
		    cout<<"The file $filename exists"<<endl;;
		} else {
		    cout<<"The file $filename does not exist"<<endl;
		}
       		proc->clearParameters(true);
		proc->clearProperties();
            }

void exampleSimple3(SaxonProcessor * saxonProc, XsltProcessor  *proc){
		cout<<"<b>exampleSimple3:</b><br/>"<<endl;
		proc->clearParameters(true);
		proc->clearProperties();
                XdmNode * xdmNode = saxonProc->parseXmlFromString("<doc><b>text value of out</b></doc>");
		if(xdmNode == NULL) {
			cout<<"xdmNode is null'"<<endl;
			return;	
		}            
		proc->setSourceFromXdmValue((XdmItem*)xdmNode);
		cout<<"end of exampleSimple3"<<endl;
		proc->clearParameters(true);
		proc->clearProperties();
}

void exampleParam(SaxonProcessor * saxonProc, XsltProcessor  *proc){
                cout<< "\nExampleParam:</b><br/>"<<endl;
		proc->setSourceFromFile("../php/xml/foo.xml");
                proc->compileFromFile("../php/xsl/foo.xsl");
            
		XdmAtomicValue * xdmvalue = saxonProc->makeStringValue("Hello to you");
		if(xdmvalue !=NULL){
			
			proc->setParameter("a-param", (XdmValue*)xdmvalue);
		} else {
			cout<< "Xdmvalue is null"<<endl;
		}
                const char * result = proc->transformToString();
		if(result != NULL) {                
			cout<<"Output:"<<result<<endl;
		} else {
			cout<<"Result is NULL<br/>"<<endl;
		}
               
                //proc->clearParameters();                
                //unset($result);
                //echo 'again with a no parameter value<br/>';
		
		proc->setProperty("!indent", "yes"); 
                const char *result2 = proc->transformToString();
               
                proc->clearProperties();
		if(result2 != NULL) {                
			cout<<result2<<endl;
		}
               
              //  unset($result);
               // echo 'again with no parameter and no properties value set. This should fail as no contextItem set<br/>';
                XdmAtomicValue * xdmValue2 = saxonProc->makeStringValue("goodbye to you");
		proc->setParameter("a-param", (XdmValue*)xdmValue2);
		
                const char *result3 = proc->transformToString();   
		if(result3 != NULL) {             
                	cout<<"Output ="<<result3<<endl;
		} else {
			cout<<"Error in result"<<endl;
		}
		proc->clearParameters();
		proc->clearProperties(); 
                        
            }



int main()
{

    SaxonProcessor * processor = new SaxonProcessor(true);
    cout<<"Test: XsltProcessor with Saxon version="<<processor->version()<<endl<<endl; 
    //processor->setcwd("/home");
   processor->setConfigurationProperty("http://saxon.sf.net/feature/generateByteCode","false");

    XsltProcessor * trans = processor->newXsltProcessor();
   exampleSimple1Err(trans);
   exampleSimple1(trans);
    exampleSimple2(trans);
    exampleSimple3(processor, trans);

    testTransformToString1(processor, trans);

    testTransformToString2(processor, trans);

    testTransformToString2a(processor, trans);

    testTransformToString2b(processor, trans);

    testTransformToString3(processor, trans);
	
    testTransformToString4(processor, trans);

    testTransfromFromstring(processor, trans);

    testTransfromFromstring2Err(processor, trans);

    testTrackingOfValueReference(processor, trans);

    testTrackingOfValueReferenceError(processor, trans);

    testXdmNodeOutput(trans);

    exampleParam(processor, trans);

testTransformToStringExtensionFunc(processor, trans);

    delete trans;
delete processor;
    // processor->release();


 SaxonProcessor * processor2 = new SaxonProcessor(true);
    cout<<"Test2: XsltProcessor with Saxon version="<<processor2->version()<<endl<<endl; 
    //processor->setcwd("/home");
    

    XsltProcessor * trans2 = processor2->newXsltProcessor();
testTransformToString1(processor2, trans2);
  delete trans2;
     processor2->release();

    return 0;
}
