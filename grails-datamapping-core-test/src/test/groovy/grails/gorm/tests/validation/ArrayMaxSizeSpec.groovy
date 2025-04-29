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

package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.model.MappingContext

/**
 * Created by graemerocher on 23/08/2017.
 */
class ArrayMaxSizeSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([ArrayEntity])
    }

    void "test size validation"() {

        given:
        MappingContext context = manager.session.datastore.mappingContext
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, manager.session.datastore.getConnectionSources().getDefaultConnectionSource().settings))
        ArrayEntity invalid = new ArrayEntity(field: "foo", bytes: new byte[0], stringArray: new String[0])

        when:
        invalid.validate()

        then:
        invalid.hasErrors()
        invalid.errors.getFieldError('bytes')
        invalid.errors.getFieldError('stringArray')

    }
}

@Entity
class ArrayEntity {

    String field
    byte[] bytes
    String[] stringArray

    static constraints = {
        bytes(minSize: 1, maxSize: 1024 * 1024 * 10)
        stringArray(minSize: 2, maxSize: 3)
    }
}