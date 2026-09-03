/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.benchmarks.controllers

import org.codehaus.groovy.control.CompilationUnit

import jakarta.servlet.ServletContext

import org.springframework.mock.web.MockHttpServletRequest

import grails.compiler.ast.ClassInjector
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.grails.compiler.web.ControllerActionTransformer

/**
 * Compiles controller sources through the real {@code ControllerActionTransformer}, so that the
 * bytecode a benchmark invokes is the bytecode a Grails application would run.
 *
 * This is the same arrangement {@code ControllerActionTransformerSpec} uses: a
 * {@code GrailsAwareClassLoader} whose only URL-driven injector is the controller action
 * transformer, with {@code shouldInject} forced on because sources compiled from a String have no
 * {@code grails-app/controllers} URL to recognise. The {@code @Artefact('Controller')} annotation in
 * each source still drives the normal annotation-driven injection, which is what applies the
 * {@code Controller} trait.
 */
class ControllerFixture {

    static GroovyClassLoader createTransformingClassLoader() {
        ControllerActionTransformer transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) {
                true
            }
        }
        transformer.compilationUnit = new CompilationUnit()
        new GrailsAwareClassLoader().tap {
            classInjectors = [transformer] as ClassInjector[]
        }
    }

    static AttributeCountingRequest createCountingRequest(ServletContext servletContext, String method, String requestUri) {
        new AttributeCountingRequest(servletContext, method, requestUri)
    }
}

/**
 * A mock request that counts attribute operations, used once outside the measured region to report
 * how much request-attribute bookkeeping a generated controller action actually performs.
 *
 * This exists so a benchmark can state what it measures rather than assert it: the count printed
 * during setup is the difference the generated code makes, independent of the timing numbers.
 */
class AttributeCountingRequest extends MockHttpServletRequest {

    private int getAttributeCount

    private int setAttributeCount

    private int removeAttributeCount

    AttributeCountingRequest(ServletContext servletContext, String method, String requestUri) {
        super(servletContext, method, requestUri)
    }

    @Override
    Object getAttribute(String name) {
        this.getAttributeCount++
        super.getAttribute(name)
    }

    @Override
    void setAttribute(String name, Object value) {
        this.setAttributeCount++
        super.setAttribute(name, value)
    }

    @Override
    void removeAttribute(String name) {
        this.removeAttributeCount++
        super.removeAttribute(name)
    }

    void resetCounts() {
        this.getAttributeCount = 0
        this.setAttributeCount = 0
        this.removeAttributeCount = 0
    }

    String describeCounts() {
        "getAttribute=${this.getAttributeCount} setAttribute=${this.setAttributeCount} removeAttribute=${this.removeAttributeCount}"
    }
}
