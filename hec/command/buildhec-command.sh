#!/bin/sh

#Build file for Saxon/C on C++

#jdkdir=/usr/lib/jvm/java-7-oracle/include

# $jdkdir/bin/javac MyClassInDll.java

#jc =p MyDll.prj
#rm -rf MyDll_jetpdb
export JET_HOME=/usr/lib/rt
export PATH=$JET_HOME/bin:$PATH
export LD_LIBRARY_PATH=$JET_HOME/lib/x86/shared:$LD_LIBRARY_PATH



gcc  Transform.c -o transform -ldl -lc -lsaxonhec -DHEC $1 $2

gcc  Query.c -o query -ldl -lc -lsaxonhec -DHEC $1 $2

