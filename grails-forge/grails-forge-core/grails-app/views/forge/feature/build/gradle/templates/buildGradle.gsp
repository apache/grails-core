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
<%@ page import="org.grails.forge.application.ApplicationType" %>
<%@ page import="org.grails.forge.build.dependencies.CoordinateResolver" %>
<%@ page import="org.grails.forge.application.Project" %>
<%@ page import="org.grails.forge.build.dependencies.Coordinate" %>
<%@ page import="org.grails.forge.build.gradle.GradleBuild" %>
<%@ page import="org.grails.forge.build.gradle.GradleDsl" %>
<%@ page import="org.grails.forge.build.gradle.GradlePlugin" %>
<%@ page import="org.grails.forge.feature.Features" %>
<%@ page import="org.grails.forge.util.VersionInfo" %>
<%@ page import="org.grails.forge.options.JdkVersion" %>
<%@ page import="org.grails.forge.build.gradle.GradleDependency" %>
<%@ page import="org.grails.forge.build.gradle.GradleRepository" %>
<%@ page import="org.grails.forge.template.GspView" %>
<%@ model="ApplicationType applicationType" %>
<%@ model="Project project" %>
<%@ model="Function<String,Coordinate> coordinateResolver" %>
<%@ model="Features features" %>
<%@ model="GradleBuild gradleBuild" %>
<%@ model="String grailsVersion" %>
<% if (!features.contains('gradle-build-src') && !gradleBuild.getBuildscriptDependencies().isEmpty()) { %>
buildscript {
    repositories {
        <% for (GradleRepository repo : gradleBuild.getBuildRepositories()) { %>
        ${repo.toSnippet('        ')}
        <% } %>
    }
    dependencies { // Not Published to Gradle Plugin Portal
        <% for (GradleDependency dependency : gradleBuild.getBuildscriptDependencies()) { %>
        ${dependency.toSnippet()}
        <% } %>
    }
}

<% } %>
<% for (String importLine : gradleBuild.getPluginsImports()) { %>
${importLine}<% } %>plugins {
    <% for (GradlePlugin gradlePlugin : gradleBuild.getPluginsWithoutApply()) { %>
    <% if (gradlePlugin.getVersion() != null) { %>
    id "${gradlePlugin.getId()}" version "${gradlePlugin.getVersion()}"
    <% } else { %>
    id "${gradlePlugin.getId()}"
    <% } %>
    <% } %>
}

<% if (!gradleBuild.getPluginsWithApply().isEmpty()) { %>
// Not Published to Gradle Plugin Portal
<% for (GradlePlugin gradlePlugin : gradleBuild.getPluginsWithApply()) { %>
apply plugin: "${gradlePlugin.getId()}"
<% } %>
<% } %>

group = "${project.getPackageName()}"

<% if (features.contains('asciidoctor')) { %>
apply from: "gradle/asciidoc.gradle"
<% } %>
repositories {
    <% for (GradleRepository repo : gradleBuild.getRepositories()) { %>
    ${repo.toSnippet('    ')}
    <% } %>
}

${GspView.of('/forge/feature/build/gradle/templates/dependencies.gsp', [applicationType: applicationType, project: project, features: features, gradleBuild: gradleBuild]).render()}
compileJava.options.release = ${features.getTargetJdk()}

<% if (features.contains('jrebel')) { %>
tasks.named('bootRun') {
    dependsOn(generateRebel)
    if (project.hasProperty("rebelAgent")) {
        jvmArgs(rebelAgent)
    }
}

<% } %>
<% if (features.contains('spock')) { %>
tasks.withType(Test).configureEach {
    useJUnitPlatform()
    <% if (features.contains('geb-with-local-browsers')) { %>
    def gebEnv = providers.systemProperty('geb.env')
    if (gebEnv.present) {
        systemProperty('geb.env', gebEnv.get())
    }
    <% } %>
}
<% } %>
${gradleBuild.renderExtensions()}
<% if (features.contains('grails-compile-static')) { %>
grails {
    compileStatic {
        controllers = true
        services = true
        tagLibs = true
        <% if (features.contains('gsp')) { %>
        gsp = true
        <% } %>
    }
}

<% } %>
<% if (features.contains('asset-pipeline-grails')) { %>
assets {
    <% if (applicationType == ApplicationType.WEB_PLUGIN) { %>
    packagePlugin = true
    <% } %>
    excludes = [
            'webjars/jquery/**',
            'webjars/bootstrap/**',
            'webjars/bootstrap-icons/**'
    ]
    includes = [
            'webjars/jquery/*/dist/jquery.js',
            'webjars/bootstrap/*/dist/js/bootstrap.bundle.js',
            'webjars/bootstrap/*/dist/css/bootstrap.css',
            'webjars/bootstrap-icons/*/font/bootstrap-icons.css',
            'webjars/bootstrap-icons/*/font/fonts/*',
    ]
}

<% } %>
