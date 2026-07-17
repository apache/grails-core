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
package grails.boot.config

import java.io.InputStream
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.Field
import java.net.URL
import java.net.URLClassLoader

import grails.boot.config.excluded.ExcludedArtefact
import grails.boot.config.indexed.IncludedArtefact
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer
import spock.lang.Specification

class ApplicationArtefactScannerSpec extends Specification {

    private final List<File> temporaryDirectories = []
    private final List<URLClassLoader> classLoaders = []
    private Collection<String> transformedClassNames

    void setup() {
        transformedClassNames = new ArrayList<>(knownTransformedClassNames())
    }

    void 'uses indexed artefacts in declaration order'() {
        given:
        Class<?> applicationClass = applicationClass('''
grails.boot.config.IndexedSecondArtefact
grails.boot.config.IndexedFirstArtefact
''')

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        indexedClassNames(classes) == [
                IndexedSecondArtefact.name,
                IndexedFirstArtefact.name
        ]
    }

    void 'deduplicates indexed artefacts while preserving the first declaration'() {
        given:
        Class<?> applicationClass = applicationClass('''
grails.boot.config.IndexedSecondArtefact
grails.boot.config.IndexedFirstArtefact
grails.boot.config.IndexedSecondArtefact
''')

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        indexedClassNames(classes) == [
                IndexedSecondArtefact.name,
                IndexedFirstArtefact.name
        ]
    }

    void 'falls back to classpath scanning when no index is present'() {
        given:
        Class<?> applicationClass = applicationClass(null)

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(FallbackArtefact.name)
    }

    void 'falls back to classpath scanning when an index is malformed'() {
        given:
        Class<?> applicationClass = applicationClass('''
grails.boot.config.IndexedFirstArtefact

grails.boot.config.IndexedSecondArtefact
''')

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(FallbackArtefact.name)
    }

    void 'falls back to application scanning when only a dependency index is present'() {
        given:
        Class<?> applicationClass = applicationClass(null, ForeignArtefact.name)

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(FallbackArtefact.name)
        !classes*.name.contains(ForeignArtefact.name)
    }

    void 'does not include entries from a dependency index'() {
        given:
        Class<?> applicationClass = applicationClass(IndexedFirstArtefact.name, ForeignArtefact.name)

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(IndexedFirstArtefact.name)
        !classes*.name.contains(ForeignArtefact.name)
    }

    void 'filters indexed artefacts by package names'() {
        given:
        Class<?> applicationClass = applicationClass("""
${IncludedArtefact.name}
${ExcludedArtefact.name}
""")

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass, [IncludedArtefact.package.name])

        then:
        classes*.name.contains(IncludedArtefact.name)
        !classes*.name.contains(ExcludedArtefact.name)
    }

    void 'falls back to classpath scanning when an indexed class is missing'() {
        given:
        Class<?> applicationClass = applicationClass('grails.boot.config.MissingArtefact')

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(FallbackArtefact.name)
    }

    void 'falls back to classpath scanning when an indexed class has a linkage error'() {
        given:
        Class<?> applicationClass = applicationClass('grails.boot.config.LinkageErrorArtefact', null, 'grails.boot.config.LinkageErrorArtefact')

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        classes*.name.contains(FallbackArtefact.name)
    }

    void 'appends transformed artefacts to indexed artefacts without duplicates'() {
        given:
        AbstractGrailsArtefactTransformer.addToTransformedClasses(IndexedFirstArtefact.name)
        AbstractGrailsArtefactTransformer.addToTransformedClasses(IndexedSecondArtefact.name)
        Class<?> applicationClass = applicationClass(IndexedFirstArtefact.name)

        when:
        Collection<Class> classes = ApplicationArtefactScanner.scanApplicationClasses(applicationClass)

        then:
        indexedClassNames(classes) == [
                IndexedFirstArtefact.name,
                IndexedSecondArtefact.name
        ]
    }

    void cleanup() {
        classLoaders.each { URLClassLoader classLoader -> classLoader.close() }
        temporaryDirectories.each { File directory -> directory.deleteDir() }
        Collection<String> knownTransformedClassNames = knownTransformedClassNames()
        knownTransformedClassNames.clear()
        knownTransformedClassNames.addAll(transformedClassNames)
    }

    private Class<?> applicationClass(String index, String dependencyIndex = null, String linkageErrorClassName = null) {
        File directory = File.createTempDir()
        temporaryDirectories << directory
        [IndexedApplication, IndexedFirstArtefact, IndexedSecondArtefact, FallbackArtefact, ArtefactMarker, IncludedArtefact, ExcludedArtefact].each {
            Class<?> type -> copyClass(type, directory)
        }
        if (index != null) {
            writeIndex(directory, index)
        }
        ClassLoader parent = ApplicationArtefactScannerSpec.classLoader
        if (dependencyIndex != null) {
            File dependencyDirectory = File.createTempDir()
            temporaryDirectories << dependencyDirectory
            copyClass(ForeignArtefact, dependencyDirectory)
            writeIndex(dependencyDirectory, dependencyIndex)
            URLClassLoader dependencyClassLoader = new URLClassLoader([dependencyDirectory.toURI().toURL()] as URL[], parent)
            classLoaders << dependencyClassLoader
            parent = dependencyClassLoader
        }
        URLClassLoader classLoader = new TestApplicationClassLoader([directory.toURI().toURL()] as URL[], parent, linkageErrorClassName)
        classLoaders << classLoader
        return classLoader.loadClass(IndexedApplication.name)
    }

    private static void writeIndex(File directory, String index) {
        File indexFile = new File(directory, ArtefactIndexReader.RESOURCE_NAME)
        indexFile.parentFile.mkdirs()
        indexFile.text = index.stripIndent().trim()
    }

    private static Collection<String> knownTransformedClassNames() {
        Field field = AbstractGrailsArtefactTransformer.getDeclaredField('KNOWN_TRANSFORMED_CLASSES')
        field.accessible = true
        (Collection<String>) field.get(null)
    }

    private static void copyClass(Class<?> type, File directory) {
        String resourceName = type.name.replace('.', '/') + '.class'
        InputStream inputStream = type.classLoader.getResourceAsStream(resourceName)
        try {
            File classFile = new File(directory, resourceName)
            classFile.parentFile.mkdirs()
            classFile.bytes = inputStream.bytes
        } finally {
            inputStream.close()
        }
    }

    private static List<String> indexedClassNames(Collection<Class> classes) {
        classes*.name.findAll { String className ->
            className == IndexedFirstArtefact.name || className == IndexedSecondArtefact.name
        }
    }
}

class IndexedApplication {
}

class IndexedFirstArtefact {
}

class IndexedSecondArtefact {
}

class ForeignArtefact {
}

@ArtefactMarker
class FallbackArtefact {
}

@Retention(RetentionPolicy.RUNTIME)
@interface ArtefactMarker {
}

class TestApplicationClassLoader extends URLClassLoader {

    private final String linkageErrorClassName

    TestApplicationClassLoader(URL[] urls, ClassLoader parent, String linkageErrorClassName) {
        super(urls, parent)
        this.linkageErrorClassName = linkageErrorClassName
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name == linkageErrorClassName) {
            throw new NoClassDefFoundError(name)
        }
        if (!isFixtureClass(name)) {
            return super.loadClass(name, resolve)
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name)
            if (loadedClass == null) {
                try {
                    loadedClass = findClass(name)
                } catch (ClassNotFoundException ignored) {
                    return super.loadClass(name, resolve)
                }
            }
            if (resolve) {
                resolveClass(loadedClass)
            }
            return loadedClass
        }
    }

    @Override
    URL getResource(String name) {
        if (isFixtureClassResource(name)) {
            URL resource = findResource(name)
            if (resource != null) {
                return resource
            }
        }
        return super.getResource(name)
    }

    private static boolean isFixtureClass(String name) {
        name.startsWith('grails.boot.config.Indexed') || name.startsWith('grails.boot.config.indexed.') || name.startsWith('grails.boot.config.excluded.') || name == FallbackArtefact.name || name == ArtefactMarker.name
    }

    private static boolean isFixtureClassResource(String name) {
        name.endsWith('.class') && isFixtureClass(name.substring(0, name.length() - '.class'.length()).replace('/', '.'))
    }
}
