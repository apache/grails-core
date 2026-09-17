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
package grails.gorm.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import spock.lang.Specification
import spock.lang.Unroll

class AnnotationMetadataSpec extends Specification {

    @Unroll
    void "#annotationType.simpleName is retained at runtime and targets FIELD"() {
        given:
        def retention = annotationType.getAnnotation(java.lang.annotation.Retention)
        def target = annotationType.getAnnotation(java.lang.annotation.Target)

        expect:
        retention.value() == RetentionPolicy.RUNTIME
        target.value() == [ElementType.FIELD] as ElementType[]

        where:
        annotationType << [CreatedBy, CreatedDate, LastModifiedBy, LastModifiedDate]
    }

    @Unroll
    void "#annotationType.simpleName is retained at runtime, targets TYPE, and drives #transformationClass"() {
        given:
        def retention = annotationType.getAnnotation(java.lang.annotation.Retention)
        def target = annotationType.getAnnotation(java.lang.annotation.Target)
        def astTransform = annotationType.getAnnotation(GroovyASTTransformationClass)

        expect:
        retention.value() == RetentionPolicy.RUNTIME
        target.value() == [ElementType.TYPE] as ElementType[]
        astTransform.value() == [transformationClass] as String[]

        where:
        annotationType | transformationClass
        Entity         | 'org.grails.compiler.gorm.GormEntityTransformation'
        JpaEntity      | 'org.grails.compiler.gorm.JpaGormEntityTransformation'
    }
}
