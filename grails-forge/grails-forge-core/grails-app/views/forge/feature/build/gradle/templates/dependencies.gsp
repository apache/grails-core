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
<%@ page import="org.grails.forge.application.ApplicationType" %>
<%@ page import="org.grails.forge.application.Project" %>
<%@ page import="org.grails.forge.build.gradle.GradleBuild" %>
<%@ page import="org.grails.forge.build.gradle.GradleDependency" %>
<%@ page import="org.grails.forge.feature.Features" %>
<%@ model="ApplicationType applicationType" %>
<%@ model="Project project" %>
<%@ model="Features features" %>
<%@ model="GradleBuild gradleBuild" %>
dependencies {
    <% for (GradleDependency dependency : gradleBuild.getDependencies()) { %>
    ${dependency.toSnippet()}
    <% } %>
}
