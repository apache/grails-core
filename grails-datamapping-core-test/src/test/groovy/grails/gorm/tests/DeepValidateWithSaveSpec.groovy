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
package grails.gorm.tests

import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.grails.datastore.gorm.validation.CascadingValidator

class DeepValidateWithSaveSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([TestEntity])
    }

    void "save delegates deepValidate:true to CascadingValidator"() {
        given: "a CascadingValidator mock installed for TestEntity"
        def persistentEntity = manager.session.mappingContext.persistentEntities.find { it.javaClass == TestEntity }
        def mockValidator = Mock(CascadingValidator)
        mockValidator.supports(_) >> true
        manager.session.mappingContext.addEntityValidator(persistentEntity, mockValidator)
        def entity = new TestEntity(name: 'test', age: 10, child: new ChildEntity(name: 'child'))

        when: "saved with deepValidate: true"
        entity.save(deepValidate: true)

        then: "CascadingValidator is called with cascade=true"
        1 * mockValidator.validate(entity, _, true)
    }


    void "save delegates deepValidate:false to CascadingValidator"() {
        given: "a CascadingValidator mock installed for TestEntity"
        def persistentEntity = manager.session.mappingContext.persistentEntities.find { it.javaClass == TestEntity }
        def mockValidator = Mock(CascadingValidator)
        mockValidator.supports(_) >> true
        manager.session.mappingContext.addEntityValidator(persistentEntity, mockValidator)
        def entity = new TestEntity(name: 'test', age: 10, child: new ChildEntity(name: 'child'))

        when: "saved with deepValidate: false"
        entity.save(deepValidate: false)

        then: "CascadingValidator is called with cascade=false"
        1 * mockValidator.validate(entity, _, false)
    }
}
