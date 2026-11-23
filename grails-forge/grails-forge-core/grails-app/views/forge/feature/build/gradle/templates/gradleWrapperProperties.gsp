%{--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--}%
<%@ page trimLogicLines="true" expressionCodec="none" %>
<%@ page import="java.util.function.Function" %>
<%@ page import="org.grails.forge.application.Project" %>
<%@ page import="org.grails.forge.build.dependencies.CoordinateResolver" %>
<%@ page import="org.grails.forge.build.dependencies.Coordinate" %>
<%@ page import="org.grails.forge.build.gradle.GradleBuild" %>
<%@ page import="org.grails.forge.build.gradle.GradlePlugin" %>
<%@ page import="org.grails.forge.feature.Features" %>
<%@ model="Project project" %>
<%@ model="GradleBuild gradleBuild" %>
<%@ model="Function<String,Coordinate> coordinateResolver" %>
<%@ model="Features features" %>
<%@ model="String gradleVersion" %>
%{-- The version is gradleToolingApiVersion of the gradle.properties of the root of the Grails repository, see VersionInfo.getGradleVersion() --}%
%{-- distributionSha256Sum is the SHA-256 Gradle publishes for that distribution (gradle-<version>-bin.zip.sha256). With a new version, --}%
%{-- add its line to src/test/resources/org/grails/forge/feature/build/gradle/gradle-distribution-sha256.properties: --}%
%{-- GradleWrapperPropertiesSpec holds the two to each other, and with -PverifyGradleChecksums to what Gradle publishes --}%
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b
distributionUrl=https\://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
