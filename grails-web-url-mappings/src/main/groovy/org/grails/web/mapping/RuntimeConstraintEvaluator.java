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
package org.grails.web.mapping;

import groovy.lang.Closure;

import org.springframework.web.context.request.RequestContextHolder;

import org.grails.web.servlet.mvc.GrailsWebRequest;

/**
 * Resolves the value a URL mapping captured for one of its named tokens - the {@code $controller},
 * {@code $action} or {@code $namespace} segments of a mapping such as
 * {@code "/$controller/$action?/$id?"}.
 *
 * <p>A {@link RegexUrlMapping} is built once and shared by every request it matches, so an evaluator
 * held by the mapping cannot hold the captured value itself. {@link AbstractUrlMappingInfo}, which is
 * created per match and does hold it, recognises this type and resolves the name straight from its own
 * parameters. That keeps {@link grails.web.mapping.UrlMappingInfo#getControllerName()} and its siblings
 * a function of the mapping and the matched URI, rather than of whatever happens to be bound to the
 * current thread.</p>
 *
 * <p>Calling the closure directly falls back to the parameters of the current request, which is all a
 * shared instance can do on its own.</p>
 *
 * @since 8.0
 */
class RuntimeConstraintEvaluator extends Closure<Object> {

    private static final long serialVersionUID = -2404119898659287216L;

    private final String constraintName;

    RuntimeConstraintEvaluator(Object owner, String constraintName) {
        super(owner);
        this.constraintName = constraintName;
    }

    /**
     * @return the name of the constrained property this evaluator resolves
     */
    String getConstraintName() {
        return constraintName;
    }

    /**
     * Resolves the token from the current request's parameters. {@link Closure#call()} lands here
     * through the closure metaclass, which resolves {@code doCall} by name; the method is deliberately
     * not a {@code call(Object...)} override so that a statically-typed override returning request
     * parameters does not exist on the {@code Closure} type surface.
     */
    public Object doCall(Object... args) {
        GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes();
        return webRequest.getParams().get(constraintName);
    }
}
