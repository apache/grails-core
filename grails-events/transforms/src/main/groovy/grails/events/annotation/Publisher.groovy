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
package grails.events.annotation

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import org.springframework.transaction.event.TransactionPhase

import org.grails.datastore.gorm.transform.GormASTTransformationClass

/**
 * Transforms a method so the return value is emitted as an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.events.transform.PublisherTransform")
@interface Publisher {
    /**
     * @return The id of the event
     */
    String value() default ""

    /**
     * @return The transaction phase to subscribe on
     */
    TransactionPhase phase() default TransactionPhase.AFTER_COMMIT

    /**
     * @return The id of the event to notify in the case of an error
     */
    String error() default ""

}
