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
package grails.openapi

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController

import spock.lang.Specification
import spock.lang.Unroll

class IdentifierSchemaSpec extends Specification {

    @Unroll
    void 'describes an identifier declared as #declared as #type #format'() {
        when:
        def openApi = OpenApiFixture.document([controller], [entity]) {
            '/lockers'(resources: resourceName)
        }

        then: 'the path the resource is addressed at'
        with(openApi.paths['/lockers/{id}'].get.parameters.find { it.name == 'id' }.schema) {
            OpenApiFixture.typeOf(it) == type
            it.format == format
        }

        and: 'the identifier the resource is rendered with'
        with(openApi.components.schemas[entity.simpleName].properties.id) {
            OpenApiFixture.typeOf(it) == type
            it.format == format
        }

        where:
        declared  | controller              | entity        || type      | format
        'Long'    | LongLockerController    | LongLocker    || 'integer' | 'int64'
        'Integer' | IntegerLockerController | IntegerLocker || 'integer' | 'int32'
        'UUID'    | UuidLockerController    | UuidLocker    || 'string'  | 'uuid'
        'String'  | StringLockerController  | StringLocker  || 'string'  | null

        resourceName = entity.simpleName.uncapitalize()
    }

    @Unroll
    void 'describes an association to an entity identified by #declared as that identifier'() {
        when:
        def openApi = OpenApiFixture.document([LockerKeyController], [LockerKey, LongLocker, IntegerLocker,
                                                                      UuidLocker, StringLocker]) {
            '/keys'(resources: 'lockerKey')
        }

        then:
        with(openApi.components.schemas['LockerKey'].properties[property].properties.id) {
            OpenApiFixture.typeOf(it) == type
            it.format == format
        }

        where:
        declared  | property        || type      | format
        'Long'    | 'longLocker'    || 'integer' | 'int64'
        'Integer' | 'integerLocker' || 'integer' | 'int32'
        'UUID'    | 'uuidLocker'    || 'string'  | 'uuid'
        'String'  | 'stringLocker'  || 'string'  | null
    }

    void 'describes a path variable other than the identifier as a string'() {
        when:
        def openApi = OpenApiFixture.document([LongLockerController], [LongLocker]) {
            "/sites/$site/lockers/$id"(controller: 'longLocker', action: 'show')
        }

        then:
        def parameters = openApi.paths['/sites/{site}/lockers/{id}'].get.parameters
        OpenApiFixture.typeOf(parameters.find { it.name == 'site' }.schema) == 'string'
        OpenApiFixture.typeOf(parameters.find { it.name == 'id' }.schema) == 'integer'
    }
}

@Entity
class LongLocker {
    String label
}

@Entity
class IntegerLocker {
    Integer id
    String label
}

@Entity
class UuidLocker {
    UUID id
    String label
}

@Entity
class StringLocker {
    String id
    String label
}

@Entity
class LockerKey {
    LongLocker longLocker
    IntegerLocker integerLocker
    UuidLocker uuidLocker
    StringLocker stringLocker
}

@Artefact('Controller')
class LongLockerController extends RestfulController<LongLocker> {
    LongLockerController() { super(LongLocker) }
}

@Artefact('Controller')
class IntegerLockerController extends RestfulController<IntegerLocker> {
    IntegerLockerController() { super(IntegerLocker) }
}

@Artefact('Controller')
class UuidLockerController extends RestfulController<UuidLocker> {
    UuidLockerController() { super(UuidLocker) }
}

@Artefact('Controller')
class StringLockerController extends RestfulController<StringLocker> {
    StringLockerController() { super(StringLocker) }
}

@Artefact('Controller')
class LockerKeyController extends RestfulController<LockerKey> {
    LockerKeyController() { super(LockerKey) }
}
