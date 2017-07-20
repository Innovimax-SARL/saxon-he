#!/bin/sh

#Build file for Saxon/C on C++

#jdkdir=/usr/lib/jvm/java-7-oracle/include

# $jdkdir/bin/javac MyClassInDll.java

#jc =p MyDll.prj
#rm -rf MyDll_jetpdb
export JET_HOME=/usr/lib/rt
export PATH=$JET_HOME/bin:$PATH
export LD_LIBRARY_PATH=$JET_HOME/lib/x86/shared:$LD_LIBRARY_PATH

mkdir -p bin

gcc -m64  -c ../../Saxon.C.API/SaxonCGlue.c -o bin/SaxonCGlue.o -ldl -lc  $1 $2

gcc -m64  -c ../../Saxon.C.API/SaxonCXPath.c -o bin/SaxonCXPath.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/XdmValue.cpp -o bin/XdmValue.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/XdmItem.cpp -o bin/XdmItem.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/XdmNode.cpp -o bin/XdmNode.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/XdmAtomicValue.cpp -o bin/XdmAtomicValue.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/SaxonProcessor.cpp -o bin/SaxonProcessor.o -ldl -lc  $1 $2

g++ -m64  -c ../../Saxon.C.API/XsltProcessor.cpp -o bin/XsltProcessor.o -ldl -lc  $1 $2

g++ -m64 -c ../../Saxon.C.API/XQueryProcessor.cpp -o bin/XQueryProcessor.o -ldl -lc  $1 $2

g++ -m64 -c ../../Saxon.C.API/XPathProcessor.cpp -o bin/XPathProcessor.o -ldl -lc  $1 $2

g++ -m64 -c ../../Saxon.C.API/SchemaValidator.cpp -o bin/SchemaValidator.o -ldl -lc  $1 $2

g++ -m64  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXSLT.cpp -o testXSLT -ldl -lc  $1 $2

g++ -m64  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXQuery.cpp -o testXQuery -ldl -lc  $1 $2


g++ -m64  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXPath.cpp -o testXPath -ldl -lc  $1 $2

g++ -m64  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testValidator.cpp -o testValidator -ldl -lc  $1 $2

