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
import spock.lang.Specification

/**
 * The where-query transform generates nested closures for association criteria. Those closures
 * must capture exactly the local variables they reference; historically they shared the outer
 * where-closure's VariableScope, so their captures depended on whether a later transformation
 * happened to recompute the scopes, producing nondeterministic bytecode between builds.
 */
class WhereQueryClosureCaptureSpec extends Specification {

    // These domain class names (ClosureCaptureBook/ClosureCaptureAuthor rather than the more
    // generic Book/Author) must stay unique across the test JVM. AstPropertyResolveUtils caches
    // resolved properties per-ClassNode instance (see its javadoc), which is enough to stop two
    // same-named-but-distinct ClassNodes from corrupting each other's property maps. It does not
    // cover AstUtils#isDomainClass(ClassNode): that check is @Memoized by Groovy's default,
    // equality-keyed memoize, and ClassNode#equals()/hashCode() reduce to the class name - so a
    // same-named, differently-shaped fixture compiled in another spec sharing this JVM fork
    // (forkEvery = 50) can still hand this class a stale domain-class verdict from that memo
    // cache, regardless of the property-map fix. Renaming these back to Book/Author would risk
    // exactly that order-dependent flake again.
    private static final String SERVICE_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class ClosureCaptureBookQueryService {
    protected DetachedCriteria<ClosureCaptureBook> findQueryByBookIdAndAuthorName(Serializable bookId, String authorName) {
        ClosureCaptureBook.where {
            bookId == bookId && author.name == authorName
        }
    }
}

@Entity
class ClosureCaptureBook {
    Long bookId
    ClosureCaptureAuthor author
}

@Entity
class ClosureCaptureAuthor {
    String name
}
'''

    void "association criteria closures capture only the variables they reference"() {
        given: 'a where query with an association criterion referencing one of two parameters'
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass(SERVICE_SOURCE)

        when: 'the generated closure classes for the where query are located'
        List<Class<?>> queryClosures = findQueryClosures(gcl).sort { it.name.count('$_closure') }
        Class<?> outerClosure = queryClosures.first()
        Class<?> associationClosure = queryClosures.last()

        then: 'the outer closure captures both parameters it references'
        outerClosure != null
        capturedReferenceCount(outerClosure) == 2

        and: 'the innermost association closure captures only the parameter it references'
        associationClosure.name.count('$_closure') > 1
        capturedReferenceCount(associationClosure) == 1
    }

    void "generated closure constructors are identical across compilations"() {
        when: 'the same source is compiled twice in isolated class loaders'
        List<String> firstSignatures = closureConstructorSignatures(new GroovyClassLoader())
        List<String> secondSignatures = closureConstructorSignatures(new GroovyClassLoader())

        then:
        !firstSignatures.empty
        firstSignatures == secondSignatures
    }

    private static List<String> closureConstructorSignatures(GroovyClassLoader gcl) {
        gcl.parseClass(SERVICE_SOURCE)
        findQueryClosures(gcl).collect { Class<?> closureClass ->
            Constructor<?> constructor = closureClass.declaredConstructors.first()
            "${closureClass.name.replaceAll(/^.*\$_/, '')}(${constructor.parameterTypes*.simpleName.join(', ')})".toString()
        }.sort()
    }

    private static List<Class<?>> findQueryClosures(GroovyClassLoader gcl) {
        gcl.loadedClasses.findAll { it.name.contains('_findQueryByBookIdAndAuthorName_') }
    }

    private static int capturedReferenceCount(Class<?> closureClass) {
        Constructor<?> constructor = closureClass.declaredConstructors.first()
        constructor.parameterTypes.count { it == Reference }
    }
}
