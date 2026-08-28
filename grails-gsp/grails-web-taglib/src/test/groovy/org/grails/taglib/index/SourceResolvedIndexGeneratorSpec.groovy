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
package org.grails.taglib.index

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

/**
 * A tag library commonly refers to something the same project declares, and none of those exist as
 * classes when the index is generated. Their source is compiled alongside it instead.
 *
 * <p>What is missing decides what a tag library declares: a base class carries the namespace, a trait
 * carries tags, and a parameter type decides whether a method is a tag at all. Answering with a
 * stand-in would file a tag library under the wrong namespace or leave out tags the running
 * application has, so what these check is that the answer is read rather than guessed - and that a
 * name which is simply wrong still fails.
 */
class SourceResolvedIndexGeneratorSpec extends Specification {

    @TempDir
    Path tempDir

    Path taglibs
    Path app
    Path output

    def setup() {
        taglibs = Files.createDirectories(tempDir.resolve('grails-app/taglib'))
        app = Files.createDirectories(tempDir.resolve('src/main/groovy'))
        output = Files.createDirectories(tempDir.resolve('out'))
    }

    void 'a tag library injecting a service this project declares is described'() {
        given:
        appSource('com/example/BookService.groovy', '''
            package com.example
            class BookService {
                List list() { [] }
            }
        ''')
        taglib('Injecting.groovy', '''
            import com.example.BookService
            import grails.gsp.TagLib
            @TagLib
            class InjectingTagLib {
                static namespace = 'injecting'
                BookService bookService
                def listBooks(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        descriptor('InjectingTagLib').namespace == 'injecting'
        descriptor('InjectingTagLib').tags == 'listBooks'
    }

    void 'a namespace inherited from a base class this project declares is read, not guessed'() {
        given: 'guessing would file it under the default namespace, where its tags do not exist'
        appSource('com/example/BaseTagLib.groovy', '''
            package com.example
            class BaseTagLib {
                static namespace = 'inherited'
            }
        ''')
        taglib('Child.groovy', '''
            import com.example.BaseTagLib
            import grails.gsp.TagLib
            @TagLib
            class ChildTagLib extends BaseTagLib {
                def greet(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        descriptor('ChildTagLib').namespace == 'inherited'
    }

    void 'tags a trait this project declares contributes are described'() {
        given: 'a stand-in would prevent the trait being applied, losing tags the application has'
        appSource('com/example/GreetingTags.groovy', '''
            package com.example
            trait GreetingTags {
                def hello(Map attrs) { }
            }
        ''')
        taglib('Carrying.groovy', '''
            import com.example.GreetingTags
            import grails.gsp.TagLib
            @TagLib
            class CarryingTagLib implements GreetingTags {
                static namespace = 'carrying'
                def goodbye(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        descriptor('CarryingTagLib').tags.split(',').toList().sort() ==
                ['goodbye', 'hello']
    }

    void 'a parameter type this project declares is recognised as attributes when it is a Map'() {
        given: 'runtime asks whether the type is assignable, so the index has to ask the same'
        appSource('com/example/Attrs.groovy', '''
            package com.example
            class Attrs extends LinkedHashMap<String, Object> {
            }
        ''')
        taglib('Subtyped.groovy', '''
            import com.example.Attrs
            import grails.gsp.TagLib
            @TagLib
            class SubtypedTagLib {
                static namespace = 'subtyped'
                def show(Attrs attrs) { }
            }
        ''')

        when:
        generate()

        then:
        descriptor('SubtypedTagLib').tags == 'show'
    }

    void 'a star import resolves to the type that exists rather than the first one tried'() {
        given: 'answering the first missing candidate would stop the search before the real one'
        appSource('com/example/present/Helper.groovy', '''
            package com.example.present
            class Helper {
                static String help() { 'helped' }
            }
        ''')
        taglib('Starred.groovy', '''
            import com.example.absent.*
            import com.example.present.*
            import grails.gsp.TagLib
            @TagLib
            class StarredTagLib {
                static namespace = 'starred'
                def show(Map attrs) { Helper.help() }
            }
        ''')

        when:
        generate()

        then:
        descriptor('StarredTagLib').namespace == 'starred'
        descriptor('StarredTagLib').tags == 'show'
    }

    void 'a misspelled type is not invented, and the tag library referring to it is left out'() {
        given: 'inventing it would let a description be derived from a tree that does not compile'
        taglib('Misspelled.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib
            @TagLib
            class MisspelledTagLib {
                static namespace = 'misspelled'
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')
        taglib('Fine.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class FineTagLib {
                static namespace = 'fine'
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then: 'and the one beside it is still described'
        manifest() == ['FineTagLib']
    }

    void 'a tag library referring to a source of this project that does not compile is left out'() {
        given:
        appSource('com/example/Broken.groovy', '''
            package com.example
            class Broken {
                def oops( {
            }
        ''')
        taglib('Referring.groovy', '''
            import com.example.Broken
            import grails.gsp.TagLib
            @TagLib
            class ReferringTagLib {
                static namespace = 'referring'
                Broken broken
                def show(Map attrs) { }
            }
        ''')
        taglib('Unaffected.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class UnaffectedTagLib {
                static namespace = 'unaffected'
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then: 'one unreadable source costs its own tag library, not the whole index'
        manifest() == ['UnaffectedTagLib']
    }

    void 'a namespace whose tag library could not be read is recorded as incomplete'() {
        given: 'so that a call to one of its tags is never reported as a misspelling'
        taglib('Unreadable.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib
            @TagLib
            class UnreadableTagLib {
                static namespace = 'partial'
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')
        taglib('Sibling.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class SiblingTagLib {
                static namespace = 'partial'
                def other(Map attrs) { }
            }
        ''')

        when:
        generate()

        then: 'the namespace exists, but is known to be missing some of its tags'
        indexOf().hasNamespace('partial')
        !indexOf().isNamespaceComplete('partial')
    }

    void 'nothing is recorded as incomplete when everything could be read'() {
        given:
        taglib('Whole.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class WholeTagLib {
                static namespace = 'whole'
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        indexOf().isNamespaceComplete('whole')
        indexOf().incompleteNamespaces.isEmpty()
    }

    void 'a tag library whose namespace cannot even be read leaves nothing complete'() {
        given: 'what was missed cannot be attributed, so no namespace may be treated as complete'
        taglib('Nameless.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib
            @TagLib
            class NamelessTagLib {
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')
        taglib('Other.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class OtherTagLib {
                static namespace = 'other'
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        !indexOf().isNamespaceComplete('other')
    }

    void 'a namespace named in a comment is not mistaken for the declaration'() {
        given: 'taking the comment would leave the real namespace looking complete, so a call to one'
        taglib('Commented.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib
            @TagLib
            class CommentedTagLib {
                // static namespace = 'decoy'
                static namespace = 'actual'
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then: 'of its tags that does exist would be reported as one that does not'
        !indexOf().isNamespaceComplete('actual')
        indexOf().isNamespaceComplete('decoy')
    }

    void 'a namespace field on some other class in the file is not the tag library\'s'() {
        given: 'claiming it would leave the namespace the tag library is really in looking complete'
        taglib('Neighboured.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib

            class Helper {
                static namespace = 'decoy'
            }

            @TagLib
            class NeighbouredTagLib {
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then: 'the tag library declares none of its own, so nothing is complete'
        !indexOf().isNamespaceComplete('g')
        !indexOf().isNamespaceComplete('decoy')
    }

    void 'a file holding more than one tag library claims neither namespace'() {
        given: 'which of them could not be read is not knowable'
        taglib('Pair.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib

            @TagLib
            class FirstPairTagLib {
                static namespace = 'first'
                NoSuchService service
                def show(Map attrs) { }
            }

            @TagLib
            class SecondPairTagLib {
                static namespace = 'second'
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        !indexOf().isNamespaceComplete('first')
        !indexOf().isNamespaceComplete('second')
    }

    void 'a skipped tag library in the default namespace leaves that namespace incomplete'() {
        given: 'it states no namespace, so the one it is in cannot be claimed from the source alone'
        taglib('Defaulted.groovy', '''
            import com.example.NoSuchService
            import grails.gsp.TagLib

            @TagLib
            class DefaultedTagLib {
                NoSuchService service
                def show(Map attrs) { }
            }
        ''')

        when:
        generate()

        then:
        !indexOf().isNamespaceComplete('g')
    }

    private TagLibraryIndex indexOf() {
        URLClassLoader loader = new URLClassLoader([output.toUri().toURL()] as URL[], (ClassLoader) null)
        try {
            return TagLibraryIndex.load(loader)
        }
        finally {
            loader.close()
        }
    }

    private void generate() {
        TagLibraryIndexGenerator.generate([taglibs.toFile()], [app.toFile()], output.toFile(),
                true, 'UTF-8')
    }

    private void taglib(String name, String source) {
        taglibs.resolve(name).toFile().text = source
    }

    private void appSource(String relativePath, String source) {
        Path file = app.resolve(relativePath)
        Files.createDirectories(file.parent)
        file.toFile().text = source
    }

    private List<String> manifest() {
        Properties names = new Properties()
        File file = output.resolve(TagLibraryIndex.INDEX_LOCATION + 'index.properties').toFile()
        if (file.isFile()) {
            file.withReader('UTF-8') { names.load(it) }
        }
        names.stringPropertyNames().toList().sort()
    }

    private Properties descriptor(String className) {
        Properties properties = new Properties()
        output.resolve(TagLibraryIndex.INDEX_LOCATION + className + '.properties').toFile()
                .withReader('UTF-8') { properties.load(it) }
        properties
    }
}
