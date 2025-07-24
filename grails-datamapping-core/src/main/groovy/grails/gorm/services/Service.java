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

package grails.gorm.services;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import org.grails.datastore.gorm.services.ServiceImplementer;
import org.grails.datastore.gorm.services.ServiceImplementerAdapter;

/**
 * Makes any class into a GORM {@link org.grails.datastore.mapping.services.Service}
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@GroovyASTTransformationClass("org.grails.datastore.gorm.services.transform.ServiceTransformation")
public @interface Service {

    /**
     * @return The domain class this service operates with
     */
    Class value() default Object.class;

    /**
     * @return The name of the service, by default this will the class name decapitalized. ie. BookService = bookService
     */
    String name() default "";

    /**
     * @return Any additional implementers to apply
     */
    Class<? extends ServiceImplementer>[] implementers() default {};

    /**
     * @return Any additional adapters to apply
     */
    Class<? extends ServiceImplementerAdapter>[] adapters() default {};
}
