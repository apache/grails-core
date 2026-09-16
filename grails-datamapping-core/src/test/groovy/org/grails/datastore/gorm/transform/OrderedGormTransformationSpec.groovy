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
package org.grails.datastore.gorm.transform

import org.apache.grails.common.compiler.GroovyTransformOrder

import spock.lang.Specification

/**
 * {@code OrderedGormTransformation} is the shared dispatcher every GORM annotation-driven
 * transform routes through ({@code @Tenant}, {@code @CurrentTenant}, {@code @WithoutTenant},
 * {@code @Transactional}, {@code @Rollback}, {@code @ReadOnly}), but every one of those real
 * transforms is {@code CompilationUnitAware} - so the branch of
 * {@code collectAndOrderGormTransformations} taken for a discovered transform that is NOT
 * {@code CompilationUnitAware} was never exercised. Neither was the catch block that runs when a
 * transform's {@code GormASTTransformationClass} name can't be loaded at all.
 */
class OrderedGormTransformationSpec extends Specification {

    void "a discovered transform that is not CompilationUnitAware is still collected and invoked"() {
        when: 'a class is annotated with a marker that resolves to a plain, non-CompilationUnitAware transform'
        new GroovyClassLoader().parseClass('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyNonCompilationUnitAwareTransform
            class NonCompilationUnitAwareTarget {
            }
        ''')

        then: 'the class compiles cleanly, proving the transform was collected and invoked without attempting an invalid CompilationUnitAware cast'
        noExceptionThrown()
    }

    void "a transform whose class name cannot be loaded is reported as a compile error instead of crashing"() {
        when: 'a class is annotated with a marker whose GormASTTransformationClass names a nonexistent class'
        new GroovyClassLoader().parseClass('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyUnloadableGormTransform
            class UnloadableTransformTarget {
            }
        ''')

        then: 'a normal compile error is reported rather than an internal compiler crash'
        org.codehaus.groovy.control.MultipleCompilationErrorsException e = thrown()
        e.message.contains('Could not load GORM transform')
    }

    void "priority orders the transform via GORM_TRANSFORMS_ORDER"() {
        expect:
        new OrderedGormTransformation().priority() == GroovyTransformOrder.GORM_TRANSFORMS_ORDER
    }
}
