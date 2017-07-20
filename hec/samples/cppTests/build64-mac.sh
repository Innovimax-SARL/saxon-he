#!/bin/sh

#Build file for Saxon/C on C++


jdkdir=/Library/Java/JavaVirtualMachines/jdk1.8.0_77.jdk/Contents/Home

# $jdkdir/bin/javac MyClassInDll.java

#jc =p MyDll.prj
#rm -rf MyDll_jetpdb
export JET_HOME=/usr/local/lib/rt
export PATH=$JET_HOME/bin:$PATH
export DYLD_LIBRARY_PATH=$JET_HOME/lib/lib/jetvm:$DYLD_LIBRARY_PATH
#export CPLUS_INCLUDE_PATH=/System/Library/Frameworks/JavaVM.framework/Headers
mkdir -p bin

gcc -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers  -c ../../Saxon.C.API/SaxonCGlue.c -o bin/SaxonCGlue.o $1 $2

gcc -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/SaxonCXPath.c -o bin/SaxonCXPath.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XdmValue.cpp -o bin/XdmValue.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XdmItem.cpp -o bin/XdmItem.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XdmNode.cpp -o bin/XdmNode.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XdmAtomicValue.cpp -o bin/XdmAtomicValue.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/SaxonProcessor.cpp -o bin/SaxonProcessor.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XsltProcessor.cpp -o bin/XsltProcessor.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XQueryProcessor.cpp -o bin/XQueryProcessor.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/XPathProcessor.cpp -o bin/XPathProcessor.o $1 $2

g++ -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers -c ../../Saxon.C.API/SchemaValidator.cpp -o bin/SchemaValidator.o $1 $2

g++  -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXSLT.cpp -o testXSLT -ldl  -L.  $1 $2

g++   -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXQuery.cpp -o testXQuery -ldl  -L.  $1 $2


g++  -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testXPath.cpp -o testXPath -ldl  -L.  $1 $2

g++  -m64 -fPIC -I$jdkdir/include  -I /System/Library/Frameworks/JavaVM.framework/Headers  bin/SaxonCGlue.o bin/SaxonCXPath.o bin/SaxonProcessor.o bin/XQueryProcessor.o bin/XsltProcessor.o bin/XPathProcessor.o bin/XdmValue.o bin/XdmItem.o bin/XdmNode.o bin/XdmAtomicValue.o bin/SchemaValidator.o testValidator.cpp -o testValidator -ldl  -L.  $1 $2

