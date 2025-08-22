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
package grails.testing.mixin.integration

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to bind a Hibernate session to the test thread without starting a transaction.
 * This mimics the application runtime behavior where OISV (Open Session In View) provides
 * a session but not a transaction.
 * 
 * Use this annotation when you want to test service methods that rely on having a session
 * but are not transactional themselves. This ensures test behavior matches runtime behavior.
 * 
 * @author Grails Team
 * @since 7.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@GroovyASTTransformationClass("org.grails.compiler.injection.testing.WithSessionTransformation")
public @interface WithSession {
    
    /**
     * The datasource names to bind sessions for.
     * If empty, sessions will be bound for all configured datasources.
     */
    String[] datasources() default []
}