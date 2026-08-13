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

import org.codehaus.groovy.control.MultipleCompilationErrorsException

import spock.lang.Specification

/**
 * A property-path expression such as {@code author.name} or a deeper {@code author.publisher.name} is
 * rewritten by {@code DetachedCriteriaTransformer#handleAssociationQueryViaPropertyExpression} into
 * nested {@code delegate.<association> { ... }} calls, one per path segment. Those delegate calls are
 * dynamic (routed through {@code AbstractDetachedCriteria#methodMissing}) and need the target class to be
 * GORM-enhanced against a live datastore to resolve - something this module deliberately has none of - so
 * these associations are verified structurally: the source compiles (or fails to, for the invalid-property
 * cases) and a nested closure is synthesized per association segment walked.
 */
class WhereQueryAssociationPathSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SINGLE_LEVEL_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class AssocPathSingleQueryService {
    protected DetachedCriteria<AssocPathSingleBook> findByAuthorName(String name) {
        AssocPathSingleBook.where {
            author.name == name
        }
    }
}

@Entity
class AssocPathSingleBook {
    String title
    AssocPathSingleAuthor author
}

@Entity
class AssocPathSingleAuthor {
    String name
}
'''

    private static final String MULTI_LEVEL_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class AssocPathMultiQueryService {
    protected DetachedCriteria<AssocPathMultiBook> findByPublisherName(String name) {
        AssocPathMultiBook.where {
            author.publisher.name == name
        }
    }
}

@Entity
class AssocPathMultiBook {
    String title
    AssocPathMultiAuthor author
}

@Entity
class AssocPathMultiAuthor {
    String name
    AssocPathMultiPublisher publisher
}

@Entity
class AssocPathMultiPublisher {
    String name
}
'''

    private static List<Class<?>> findQueryClosures(GroovyClassLoader gcl, String methodName) {
        gcl.loadedClasses.findAll { it.name.contains("_${methodName}_") }
    }

    void "a single-level association property path compiles and generates one nested association closure"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass(SINGLE_LEVEL_SOURCE)

        then:
        noExceptionThrown()

        and: 'one closure for the outer where-block and one nested closure for the association segment walked'
        List<Class<?>> queryClosures = findQueryClosures(gcl, 'findByAuthorName').sort { it.name.count('$_closure') }
        queryClosures.size() == 2
        queryClosures.last().name.count('$_closure') == 1
    }

    void "a multi-level association property path compiles and generates a closure per path segment"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass(MULTI_LEVEL_SOURCE)

        then:
        noExceptionThrown()

        and: 'one closure for the outer where-block, one for each of the two association segments walked'
        List<Class<?>> queryClosures = findQueryClosures(gcl, 'findByPublisherName').sort { it.name.count('$_closure') }
        queryClosures.size() == 3
        queryClosures.last().name.count('$_closure') == 2
    }

    void "querying an unknown property on a single-level association fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AssocPathUnknownPropBook {
    String title
    AssocPathUnknownPropAuthor author

    static findInvalid() {
        AssocPathUnknownPropBook.where {
            author.unknownProperty == "x"
        }
    }
}

@Entity
class AssocPathUnknownPropAuthor {
    String name
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query property "unknownProperty"')
    }

    void "querying an unknown top-level property fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AssocPathUnknownTopLevelBook {
    String title

    static findInvalid() {
        AssocPathUnknownTopLevelBook.where {
            unknownProperty == "x"
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query on property "unknownProperty"')
    }
}
