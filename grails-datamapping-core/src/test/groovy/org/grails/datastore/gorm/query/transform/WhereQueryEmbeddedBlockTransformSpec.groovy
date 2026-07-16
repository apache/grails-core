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
package org.grails.datastore.gorm.query.transform

import java.lang.reflect.Constructor

import groovy.lang.Reference
import spock.lang.Issue
import spock.lang.Specification

/**
 * An association block in a where query can address an embedded component. The
 * component's type carries no association generics, so the transform must keep
 * the component type itself when rewriting the block — a regression dropped the
 * block's criteria entirely, producing an association criteria that matched
 * everything on Hibernate and nothing on the in-memory implementation.
 */
class WhereQueryEmbeddedBlockTransformSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SERVICE_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class EmbeddedBlockQueryService {
    protected DetachedCriteria<EmbeddedBlockWorkItem> findByRefValue(String search) {
        EmbeddedBlockWorkItem.where {
            description != 'none' && extRef { value == search }
        }
    }

    protected DetachedCriteria<EmbeddedBlockWorkItem> findByTagLabel(String search) {
        EmbeddedBlockWorkItem.where {
            description != 'none' && tags { label == search }
        }
    }
}

@Entity
class EmbeddedBlockWorkItem {
    String description
    EmbeddedBlockExternalRef extRef

    static embedded = ['extRef']
    static hasMany = [tags: EmbeddedBlockTag]
}

@Entity
class EmbeddedBlockTag {
    String label
}

class EmbeddedBlockExternalRef {
    String provider
    String value
}
'''

    @Issue('https://github.com/apache/grails-core/issues/15955')
    void "an embedded association block keeps its criteria through the where transform"() {
        given: 'a where query whose embedded component block references a method parameter'
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass(SERVICE_SOURCE)

        when: 'the generated closure classes for the where query are located'
        List<Class<?>> queryClosures = findQueryClosures(gcl).sort { it.name.count('$_closure') }
        Class<?> embeddedBlockClosure = queryClosures.last()

        then: 'a nested closure was generated for the embedded block'
        embeddedBlockClosure.name.count('$_closure') > 1

        and: 'it captures the parameter its criterion references, proving the criteria were not dropped'
        capturedReferenceCount(embeddedBlockClosure) == 1
    }

    void "a collection association block keeps its criteria through the where transform"() {
        given: 'a where query whose hasMany association block references a method parameter'
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass(SERVICE_SOURCE)

        when: 'the generated closure classes for the collection where query are located'
        List<Class<?>> queryClosures = findQueryClosures(gcl, 'findByTagLabel').sort { it.name.count('$_closure') }
        Class<?> associationBlockClosure = queryClosures.last()

        then: 'the nested association closure captures the parameter its criterion references'
        associationBlockClosure.name.count('$_closure') > 1
        capturedReferenceCount(associationBlockClosure) == 1
    }

    private static List<Class<?>> findQueryClosures(GroovyClassLoader gcl, String methodName = 'findByRefValue') {
        gcl.loadedClasses.findAll {
            it.name.startsWith("EmbeddedBlockQueryService\$_${methodName}_closure")
        }.toList()
    }

    private static int capturedReferenceCount(Class<?> closureClass) {
        Constructor<?> constructor = closureClass.declaredConstructors.first()
        constructor.parameterTypes.count { it == Reference }
    }
}
