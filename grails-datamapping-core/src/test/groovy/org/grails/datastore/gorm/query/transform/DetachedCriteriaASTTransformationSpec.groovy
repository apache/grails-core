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
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification

/**
 * {@link GlobalDetachedCriteriaASTTransformation} applies the same underlying transformer to
 * every class in a compilation automatically, so a where-query never needs
 * {@link ApplyDetachedCriteriaTransform} in a real build. That makes the local, annotation-driven
 * {@link DetachedCriteriaASTTransformation} redundant whenever the global transform is also on the
 * classpath, so these specs disable the global transform to isolate the local one and prove it is,
 * on its own, both necessary and sufficient to rewrite a where-query.
 */
class DetachedCriteriaASTTransformationSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String UNANNOTATED_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class LocalTransformOffBookQueryService {
    protected DetachedCriteria<LocalTransformOffBook> findQueryByBookIdAndAuthorName(Serializable bookId, String authorName) {
        LocalTransformOffBook.where {
            bookId == bookId && author.name == authorName
        }
    }
}

@Entity
class LocalTransformOffBook {
    Long bookId
    LocalTransformOffAuthor author
}

@Entity
class LocalTransformOffAuthor {
    String name
}
'''

    private static final String ANNOTATED_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
class LocalTransformOnBookQueryService {
    protected DetachedCriteria<LocalTransformOnBook> findQueryByBookIdAndAuthorName(Serializable bookId, String authorName) {
        LocalTransformOnBook.where {
            bookId == bookId && author.name == authorName
        }
    }
}

@Entity
class LocalTransformOnBook {
    Long bookId
    LocalTransformOnAuthor author
}

@Entity
class LocalTransformOnAuthor {
    String name
}
'''

    void "an association where-query is left untransformed when neither the local nor the global transform runs"() {
        given: 'the global transform disabled and no local annotation applied'
        GroovyClassLoader gcl = newGroovyClassLoaderWithGlobalTransformDisabled()
        gcl.parseClass(UNANNOTATED_SOURCE)

        expect: 'the where-closure was compiled as-is, with no association sub-closure synthesized'
        findQueryClosures(gcl).size() == 1
    }

    void "ApplyDetachedCriteriaTransform alone rewrites the where-query even with the global transform disabled"() {
        given: 'the global transform disabled and the local annotation applied to the query service'
        GroovyClassLoader gcl = newGroovyClassLoaderWithGlobalTransformDisabled()
        gcl.parseClass(ANNOTATED_SOURCE)

        when: 'the generated closure classes for the where query are located'
        List<Class<?>> queryClosures = findQueryClosures(gcl).sort { it.name.count('$_closure') }
        Class<?> outerClosure = queryClosures.first()
        Class<?> associationClosure = queryClosures.last()

        then: 'the outer closure captures both parameters it references'
        queryClosures.size() > 1
        capturedReferenceCount(outerClosure) == 2

        and: 'the innermost association closure captures only the parameter it references'
        associationClosure.name.count('$_closure') > 1
        capturedReferenceCount(associationClosure) == 1
    }

    private static GroovyClassLoader newGroovyClassLoaderWithGlobalTransformDisabled() {
        CompilerConfiguration config = new CompilerConfiguration()
        config.disabledGlobalASTTransformations = [GlobalDetachedCriteriaASTTransformation.name] as Set<String>
        new GroovyClassLoader(DetachedCriteriaASTTransformationSpec.classLoader, config)
    }

    private static List<Class<?>> findQueryClosures(GroovyClassLoader gcl) {
        gcl.loadedClasses.findAll { it.name.contains('_findQueryByBookIdAndAuthorName_') }
    }

    private static int capturedReferenceCount(Class<?> closureClass) {
        Constructor<?> constructor = closureClass.declaredConstructors.first()
        constructor.parameterTypes.count { it == Reference }
    }
}
