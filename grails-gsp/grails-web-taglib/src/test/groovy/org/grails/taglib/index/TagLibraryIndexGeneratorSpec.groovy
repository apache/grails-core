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
 * Generating the index for a whole source set, rather than accumulating it as each class compiles,
 * is what allows a renamed or deleted tag library to disappear from it.
 */
class TagLibraryIndexGeneratorSpec extends Specification {

    @TempDir
    Path tempDir

    Path sources
    Path output

    def setup() {
        sources = Files.createDirectories(tempDir.resolve('src'))
        output = Files.createDirectories(tempDir.resolve('out'))
    }

    void 'a closure based tag is recorded as such'() {
        given:
        write('Legacy.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class LegacyTagLib {
                static namespace = 'legacy'
                def asMethod(Map attrs) { }
                Closure asClosure = { Map attrs -> }
            }
        ''')

        when:
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then: 'both forms are described by name, since both are dispatched the same way'
        descriptor('LegacyTagLib').tags == 'asClosure,asMethod'
    }

    void 'a tag library is described without being loaded or executed'() {
        given: 'a tag library whose static initialiser would fail if it ran'
        write('Explosive.groovy', '''
            import grails.gsp.TagLib
            @TagLib
            class ExplosiveTagLib {
                static { throw new RuntimeException('must not run') }
                static namespace = 'boom'
                def alpha(Map attrs) { }
                def beta(Map attrs, Closure body) { }
            }
        ''')

        when:
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then: 'its tags are described from the source alone'
        descriptor('ExplosiveTagLib').namespace == 'boom'
        descriptor('ExplosiveTagLib').tags == 'alpha,beta'
    }

    void 'a renamed tag library leaves nothing behind'() {
        given:
        write('First.groovy', taglib('OldNameTagLib', 'old', 'one'))
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')
        assert descriptorFile('OldNameTagLib').exists()

        when: 'the tag library is renamed and the index regenerated'
        Files.delete(sources.resolve('First.groovy'))
        write('First.groovy', taglib('NewNameTagLib', 'old', 'one'))
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then: 'the old descriptor is gone rather than describing a class that no longer exists'
        !descriptorFile('OldNameTagLib').exists()
        descriptorFile('NewNameTagLib').exists()

        and: 'the manifest names only what exists'
        manifest() == ['NewNameTagLib']
    }

    void 'a deleted tag library leaves nothing behind'() {
        given:
        write('Gone.groovy', taglib('GoingTagLib', 'g', 'vanishes'))
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')
        assert descriptorFile('GoingTagLib').exists()

        when:
        Files.delete(sources.resolve('Gone.groovy'))
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then:
        !descriptorFile('GoingTagLib').exists()
        manifest().isEmpty()
    }

    void 'regenerating unchanged sources produces an identical index'() {
        given:
        write('A.groovy', taglib('AlphaTagLib', 'a', 'one'))
        write('B.groovy', taglib('BetaTagLib', 'b', 'two'))

        when:
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')
        String first = descriptorFile('AlphaTagLib').text + manifestFile().text
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')
        String second = descriptorFile('AlphaTagLib').text + manifestFile().text

        then: 'byte for byte, so the build stays reproducible and up to date checks hold'
        first == second
    }

    void 'a tag library that cannot be resolved yet does not lose the others'() {
        given: 'one tag library referring to something not on the classpath, as a service in the same project is'
        write('Unresolvable.groovy', '''
            import grails.gsp.TagLib
            import com.nowhere.NotOnTheClasspath
            @TagLib
            class UnresolvableTagLib {
                static namespace = 'nope'
                NotOnTheClasspath collaborator
                def gone(Map attrs) { }
            }
        ''')
        write('Fine.groovy', taglib('FineTagLib', 'fine', 'present'))

        when:
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then: 'the one that reads is described'
        descriptorFile('FineTagLib').exists()
        descriptor('FineTagLib').tags == 'present'

        and: 'the one that does not is left out here, and describes itself when it is compiled'
        !descriptorFile('UnresolvableTagLib').exists()
        manifest() == ['FineTagLib']
    }

    void 'a class that is not a tag library is ignored'() {
        given:
        write('Service.groovy', 'class SomeService { def doThing(Map attrs) { } }')

        when:
        TagLibraryIndexGenerator.generate(sources.toFile(), output.toFile(), true, 'UTF-8')

        then:
        manifest().isEmpty()
    }

    void 'several source directories are described in one pass'() {
        given: 'tag libraries in two directories, as a project keeping some outside grails-app has'
        Path other = Files.createDirectories(tempDir.resolve('other'))
        write('First.groovy', taglib('FirstTagLib', 'first', 'one'))
        other.resolve('Second.groovy').toFile().text = taglib('SecondTagLib', 'second', 'two')

        when:
        TagLibraryIndexGenerator.generate([sources.toFile(), other.toFile()], output.toFile(), true, 'UTF-8')

        then: 'both are described'
        manifest() == ['FirstTagLib', 'SecondTagLib']
        descriptor('FirstTagLib').namespace == 'first'
        descriptor('SecondTagLib').namespace == 'second'
    }

    void 'a tag library removed from one of several directories leaves nothing behind'() {
        given: 'describing each directory in turn would either erase the last or keep the deleted one'
        Path other = Files.createDirectories(tempDir.resolve('other'))
        write('First.groovy', taglib('FirstTagLib', 'first', 'one'))
        other.resolve('Second.groovy').toFile().text = taglib('SecondTagLib', 'second', 'two')
        TagLibraryIndexGenerator.generate([sources.toFile(), other.toFile()], output.toFile(), true, 'UTF-8')

        when: 'one of them is deleted and the index regenerated'
        other.resolve('Second.groovy').toFile().delete()
        TagLibraryIndexGenerator.generate([sources.toFile(), other.toFile()], output.toFile(), true, 'UTF-8')

        then: 'only the one that still exists is described'
        manifest() == ['FirstTagLib']
    }

    private void write(String name, String source) {
        sources.resolve(name).toFile().text = source
    }

    private static String taglib(String className, String namespace, String tag) {
        """
            import grails.gsp.TagLib
            @TagLib
            class ${className} {
                static namespace = '${namespace}'
                def ${tag}(Map attrs) { }
            }
        """
    }

    private File descriptorFile(String simpleName) {
        new File(output.toFile(), TagLibraryIndex.INDEX_LOCATION + simpleName + '.properties')
    }

    private File manifestFile() {
        new File(output.toFile(), TagLibraryIndex.INDEX_LOCATION + 'index.properties')
    }

    private Properties descriptor(String simpleName) {
        def properties = new Properties()
        descriptorFile(simpleName).withReader('UTF-8') { properties.load(it) }
        properties
    }

    private List<String> manifest() {
        File file = manifestFile()
        if (!file.exists()) {
            return []
        }
        def properties = new Properties()
        file.withReader('UTF-8') { properties.load(it) }
        properties.stringPropertyNames().sort()
    }

    void 'a class pulled in only to resolve a type is not described'() {
        given: 'a helper named like a tag library, referenced as a superclass but never asked for'
        Path taglibs = Files.createDirectories(tempDir.resolve('grails-app/taglib/demo'))
        Path helpers = Files.createDirectories(tempDir.resolve('src/main/groovy/demo'))
        helpers.resolve('SharedTagLib.groovy').toFile().text = """
            package demo
            class SharedTagLib {
                def helper(Map attrs) { 'not a tag' }
            }
        """
        taglibs.resolve('RealTagLib.groovy').toFile().text = """
            package demo
            import grails.gsp.TagLib
            @TagLib
            class RealTagLib extends SharedTagLib {
                static namespace = 'real'
                def actual(Map attrs) { 'tag' }
            }
        """
        File out = Files.createDirectories(tempDir.resolve('out')).toFile()

        when: 'the helper root is a resolution root, not a source directory'
        TagLibraryIndexGenerator.generate([tempDir.resolve('grails-app/taglib').toFile()],
                [tempDir.resolve('src/main/groovy').toFile()], out, true, 'UTF-8')

        then: 'the tag library it was pointed at is described'
        new File(out, 'META-INF/grails/taglibs/demo.RealTagLib.properties').isFile()

        and: 'and the helper is not, so its methods never become tags of the default namespace'
        !new File(out, 'META-INF/grails/taglibs/demo.SharedTagLib.properties').isFile()
    }

    void 'an abstract base class is not described'() {
        given: 'a base kept beside the tag libraries that share it, which is where it belongs'
        Path taglibs = Files.createDirectories(tempDir.resolve('grails-app/taglib/demo'))
        taglibs.resolve('BaseTagLib.groovy').toFile().text = """
            package demo
            abstract class BaseTagLib {
                def common(Map attrs) { 'shared' }
            }
        """
        taglibs.resolve('MyTagLib.groovy').toFile().text = """
            package demo
            import grails.gsp.TagLib
            @TagLib
            class MyTagLib extends BaseTagLib {
                static namespace = 'my'
                def own(Map attrs) { 'mine' }
            }
        """
        File out = Files.createDirectories(tempDir.resolve('out-abstract')).toFile()

        when:
        TagLibraryIndexGenerator.generate(tempDir.resolve('grails-app/taglib').toFile(), out, true, 'UTF-8')

        then: 'artefact handling never registers an abstract class, so its methods are tags of nothing'
        !new File(out, 'META-INF/grails/taglibs/demo.BaseTagLib.properties').isFile()

        and: 'the tag library that extends it is described as usual'
        new File(out, 'META-INF/grails/taglibs/demo.MyTagLib.properties').text.contains('own')
    }

}
