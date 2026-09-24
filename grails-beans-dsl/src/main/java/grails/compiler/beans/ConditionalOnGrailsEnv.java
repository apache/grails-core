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
package grails.compiler.beans;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

import org.grails.compiler.beans.OnGrailsEnvCondition;

/**
 * Registers the annotated bean or configuration only in the named Grails environments.
 *
 * <p>This is what the {@code beans} DSL's {@code .conditionalOnGrailsEnv(...)} qualifier compiles to. It exists
 * because {@code @ConditionalOnProperty(name = "grails.env", ...)} - the obvious way to write this -
 * is only correct when the environment was set explicitly with {@code -Dgrails.env}. Grails
 * otherwise infers the current environment (development when running from a build, production from
 * a packaged jar) without that property existing anywhere, so the property-based condition silently
 * never matches and the bean is silently absent. {@link OnGrailsEnvCondition} asks Grails.</p>
 *
 * @see OnGrailsEnvCondition
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnGrailsEnvCondition.class)
public @interface ConditionalOnGrailsEnv {

    /**
     * The environment names to match, as {@code grails.util.Environment} reports them - the
     * built-in ones lowercased ({@code development}, {@code production}, {@code test},
     * {@code application}), or a custom environment's own name. Matching any one is enough.
     */
    String[] value();

}
