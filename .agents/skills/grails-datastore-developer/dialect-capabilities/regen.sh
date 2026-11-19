#!/bin/bash
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
# Regenerates ../dialect-capabilities.md from the Hibernate jars in the Gradle cache. Run it from anywhere.
# Bump H5V / H7V when the Hibernate versions of dependencies.gradle move. Needs a JDK 17+ and a warm Gradle cache.
set -e
cd "$(dirname "$0")"
H5V=5.6.15.Final; H7V=7.4.13.Final
C=~/.gradle/caches/modules-2/files-2.1
j() { find $C/$1 -name "$2" ! -name '*sources*' 2>/dev/null | sort | tail -1; }
common="$(j jakarta.persistence 'jakarta.persistence-api-3.1.0.jar'):$(j org.jboss.logging 'jboss-logging-*.jar'):$(j org.antlr 'antlr4-runtime-*.jar'):$(j net.bytebuddy 'byte-buddy-1*.jar'):$(j com.fasterxml 'classmate-*.jar'):$(j jakarta.transaction 'jakarta.transaction-api-*.jar'):$(j jakarta.inject 'jakarta.inject-api-*.jar'):$(j jakarta.xml.bind 'jakarta.xml.bind-api-*.jar'):$(j org.dom4j 'dom4j-*.jar'):$(j org.javassist 'javassist-*.jar')"
H5="$(j org.hibernate/hibernate-core-jakarta "hibernate-core-jakarta-$H5V.jar"):$(j org.hibernate.common 'hibernate-commons-annotations-5*.jar'):$(j org.jboss 'jandex-2*.jar')"
H7="$(j org.hibernate.orm/hibernate-core "hibernate-core-$H7V.jar"):$(j org.hibernate.common 'hibernate-commons-annotations-7*.jar'):$(j org.hibernate.models 'hibernate-models-1.1.2.jar'):$(j jakarta.persistence 'jakarta.persistence-api-3.2.0.jar'):$(j io.smallrye 'jandex-3*.jar')"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
javac -d "$W/out" Dump.java
java -cp "$W/out:$H5:$common" Dump "hibernate5-${H5V%.Final}" > "$W/h5.tsv" 2>/dev/null
java -cp "$W/out:$H7:$common" Dump "hibernate7-${H7V%.Final}" > "$W/h7.tsv" 2>/dev/null
python3 gen.py "$W/h5.tsv" "$W/h7.tsv" > ../dialect-capabilities.md
echo "wrote $(cd .. && pwd)/dialect-capabilities.md"
