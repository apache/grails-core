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

package org.grails.gsp.compiler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GroovyPageTypeCheckingConfig {
    String[] taglibs() default {};

    /**
     * Names the page introduces for itself, through the {@code var} and {@code status} attributes of
     * the tags it calls -- {@code <g:set var="total"/>}, {@code <g:each var="book"/>} and the like.
     *
     * <p>A page that writes one of these has declared it as plainly as it can; what it holds is
     * decided by the tag at render time and cannot be known here. They are resolved dynamically
     * rather than reported, so that using a tag to introduce a name does not require declaring it a
     * second time in the model directive.</p>
     */
    String[] pageScopeVariables() default {};

    /**
     * Whether a name the page never declares, and a member read from something whose type is not
     * known, fail the compilation rather than resolving as they would in a page that is not compiled
     * statically.
     *
     * <p>Off by default, so that compiling a page statically is worth doing on an application that
     * has not declared the model of every page: what can be checked is checked, and what cannot is
     * left to run as it always has. Turning it on asks for the guarantee instead.</p>
     */
    boolean strict() default false;
}
