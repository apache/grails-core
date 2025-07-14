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
package grails.plugin.cache;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method (or all the methods on a class) can be cached.
 *
 * <p>The method arguments and signature are used for computing the key while the
 * returned instance is used as the cache value.
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.plugin.cache.compiler.CacheableTransformation")
public @interface Cacheable {

    /**
     * Name of the caches in which the update takes place.
     * <p>May be used to determine the target cache (or caches), matching the
     * qualifier value.
     */
    String[] value();

    /**
     * A closure for computing the key dynamically.
     * <p>Default is null, meaning all method parameters are considered as a key.
     */
    Class[] key() default {};

    /**
     * A closure used for conditioning the method caching.
     * <p>Default is null, meaning the method is always cached.
     */
    Class[] condition() default {};
}
