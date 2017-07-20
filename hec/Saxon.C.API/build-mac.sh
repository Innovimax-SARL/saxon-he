#!/bin/sh

#jdkdir=/usr/lib/jvm/java-7-oracle/include

# $jdkdir/bin/javac MyClassInDll.java

#jc =p MyDll.prj
#rm -rf MyDll_jetpdb

gcc -m32 -I$jdkdir/include -I$jdkdir/include/linux -I /System/Library/Frameworks/JavaVM.framework/Headers saxonProcessor.c -o saxonProcessor -ldl -lc -lsaxon

