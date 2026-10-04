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
package org.grails.web.servlet.boostrap

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO

import grails.util.Environment
import grails.web.servlet.bootstrap.GrailsBootstrapClass
import org.grails.core.AbstractGrailsClass
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

// Not a GroovyObject: AbstractGrailsClass overrides getMetaClass() to return the wrapped artefact
// class's metaClass (by design), and a synthesized GroovyObject.invokeMethod()/getProperty() would
// dispatch dynamic calls through that (wrong) metaClass instead of this class's own. Same fix as
// DefaultGrailsCodecClass and DefaultGrailsJobClass earlier this session.
@SuppressWarnings('serial')
@POJO
@CompileStatic
class DefaultGrailsBootstrapClass extends AbstractGrailsClass implements GrailsBootstrapClass {

    public static final String BOOT_STRAP = 'BootStrap'

    private static final String INIT_CLOSURE = 'init'
    private static final String DESTROY_CLOSURE = 'destroy'

    @SuppressWarnings('rawtypes')
    private static final Closure BLANK_CLOSURE = new Closure(DefaultGrailsBootstrapClass) {
        @Override
        Object call(Object... args) { return null }
    }
    private final Object instance

    DefaultGrailsBootstrapClass(Class<?> clazz) {
        super(clazz, BOOT_STRAP)
        this.instance = super.getReferenceInstance()
    }

    @Override
    Object getReferenceInstance() {
        return this.instance
    }

    Closure<?> getInitClosure() {
        Object obj = ClassPropertyFetcher.getInstancePropertyValue(instance, INIT_CLOSURE)
        if (obj instanceof Closure) {
            return (Closure<?>) obj
        }
        return BLANK_CLOSURE
    }

    Closure<?> getDestroyClosure() {
        Object obj = ClassPropertyFetcher.getInstancePropertyValue(instance, DESTROY_CLOSURE)
        if (obj instanceof Closure) {
            return (Closure<?>) obj
        }
        return BLANK_CLOSURE
    }

    void callInit() {
        Closure<?> init = getInitClosure()
        if (init != null) {
            Environment.executeForCurrentEnvironment(init)
        }
    }

    void callDestroy() {
        Closure<?> destroy = getDestroyClosure()
        if (destroy != null) {
            Environment.executeForCurrentEnvironment(destroy)
        }
    }

}
