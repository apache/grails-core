/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.servlet.boostrap;

import grails.util.Environment;
import groovy.lang.Closure;
import org.grails.core.AbstractGrailsClass;
import grails.web.servlet.bootstrap.GrailsBootstrapClass;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;

import jakarta.servlet.ServletContext;

@SuppressWarnings("serial")
public class DefaultGrailsBootstrapClass extends AbstractGrailsClass implements GrailsBootstrapClass {

    public static final String BOOT_STRAP = "BootStrap";

    private static final String INIT_CLOSURE = "init";
    private static final String DESTROY_CLOSURE = "destroy";
    @SuppressWarnings("rawtypes")
    private static final Closure BLANK_CLOSURE = new Closure(DefaultGrailsBootstrapClass.class) {
        @Override
        public Object call(Object... args) { return null; }
    };
    private final Object instance;

    public DefaultGrailsBootstrapClass(Class<?> clazz) {
        super(clazz, BOOT_STRAP);
        this.instance = super.getReferenceInstance();
    }

    @Override
    public Object getReferenceInstance() {
        return this.instance;
    }

    public Closure<?> getInitClosure() {
        Object obj = ClassPropertyFetcher.getInstancePropertyValue(instance, INIT_CLOSURE);
        if (obj instanceof Closure) {
            return (Closure<?>)obj;
        }
        return BLANK_CLOSURE;
    }

    public Closure<?> getDestroyClosure() {
        Object obj = ClassPropertyFetcher.getInstancePropertyValue(instance, DESTROY_CLOSURE);
        if (obj instanceof Closure) {
            return (Closure<?>)obj;
        }
        return BLANK_CLOSURE;
    }

    public void callInit() {
        Closure<?> init = getInitClosure();
        if (init != null) {
            Environment.executeForCurrentEnvironment(init);
        }
    }

    public void callDestroy() {
        Closure<?> destroy = getDestroyClosure();
        if (destroy != null) {
            Environment.executeForCurrentEnvironment(destroy);
        }
    }
}
