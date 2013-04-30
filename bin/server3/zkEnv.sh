#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script should be sourced into other zookeeper
# scripts to setup the env variables

# We use ZOOCFGDIR if defined,
# otherwise we use /etc/zookeeper
# or the  directory that is
# a sibling of this script's directory
if [ "x$ZOOCFGDIR" = "x" ]
then
    if [ -d "/etc/zookeeper" ]
    then
        ZOOCFGDIR="/etc/zookeeper"
    else
        ZOOCFGDIR="$ZOOBINDIR/../../conf"
    fi
fi

if [ "x$ZOOCFG" = "x" ]
then
    ZOOCFG="zoo.cfg"
fi

ZOOCFG="$ZOOCFGDIR/$ZOOCFG"

if [ -e "$ZOOCFGDIR/java.env" ]
then
    . "$ZOOCFGDIR/java.env"
fi

if [ "x${ZOO_LOG_DIR}" = "x" ]
then
    ZOO_LOG_DIR="."
fi

if [ "x${ZOO_LOG4J_PROP}" = "x" ]
then
    #ZOO_LOG4J_PROP="INFO,CONSOLE"
    ZOO_LOG4J_PROP="NONE"
fi

if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

#add the zoocfg dir to classpath
CLASSPATH="$ZOOCFGDIR:$CLASSPATH"

for i in "$ZOOBINDIR"/../../src/java/lib/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

#make it work in the release
for i in "$ZOOBINDIR"/../../lib/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

#make it work in the release
for i in "$ZOOBINDIR"/../../zookeeper-*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

#make it work for developers
for d in "$ZOOBINDIR"/../../build/lib/*.jar
do
   CLASSPATH="$d:$CLASSPATH"
done

#make it work for recipes

CLASSPATH="$ZOOBINDIR/../../build/recipes/lock/classes:$CLASSPATH"
CLASSPATH="$ZOOBINDIR/../../build/recipes/queue/classes:$CLASSPATH"


#make it work for developers
CLASSPATH="$ZOOBINDIR/../../build/classes:$CLASSPATH"
CLASSPATH="$ZOOBINDIR/../../build/recipes/gla/classes:$CLASSPATH"

case "`uname`" in
    CYGWIN*) cygwin=true ;;
    *) cygwin=false ;;
esac

if $cygwin
then
    CLASSPATH=`cygpath -wp "$CLASSPATH"`
fi

#echo "CLASSPATH=$CLASSPATH"
