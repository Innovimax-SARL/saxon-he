#include "../../Saxon.C.API/SaxonCProcessor.h"


int main()
{
    HANDLE myDllHandle;
    //JNIEnv *(environ.env);
    //JavaVM *jvm;
    jclass  myClassInDll;
    int cap = 10;
    sxnc_parameter * parameters; /*!< array of paramaters used for the transformation as (string, value) pairs */
    parameters;
    int parLen, parCap;
     parLen = 0;
    parCap = cap;
    sxnc_property * properties; /*!< array of properties used for the transformation as (string, string) pairs */
    properties;
    int propLen, propCap;
    propCap = cap;
    propLen =0;
    sxnc_environment * environ;
    sxnc_processor * processor;

    initSaxonc(&environ, &processor, &parameters, &properties, parCap, propCap);

    /*
     * First of all, load required component.
     * By the time of JET initialization, all components should be loaded.
     */
    environ->myDllHandle = loadDefaultDll ();
	
    /*
     * Initialize JET run-time.
     * The handle of loaded component is used to retrieve Invocation API.
     */
    initDefaultJavaRT (&environ);
    //initJavaRT (environ->myDllHandle, &(environ->jvm), &(environ->env));
    const char *verCh = version(*environ);
    printf("XQuery Tests\n\nSaxon version: %s \n", verCh);

    setProperty(&properties, &propLen, &propCap, "s", "cat.xml");

    setProperty(&properties, &propLen, &propCap, "qs", "<out>{count(/out/person)}</out>");

    const char * result = executeQueryToString(*environ, &processor, NULL, 0,properties,0,propLen);

    if(!result) {
      printf("result is null\n");
    }else {
      printf("%s",result);
    }
    fflush(stdout);
    finalizeJavaRT (environ->jvm);
    freeSaxonc(&environ, &processor, &parameters, &properties);

    return 0;
}
