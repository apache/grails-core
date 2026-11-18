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
package org.grails.io.watch

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

/**
 * Tests that a {@link DirectoryWatcher} reports file changes on the classpaths an application can
 * present, exercised through the watcher's public API.
 *
 * <p>The native macOS watcher needs {@code io.methvin:directory-watcher}, which this module declares
 * {@code compileOnly}. The test runtime therefore does not carry it — matching an application that
 * has not opted in — while {@code net.java.dev.jna:jna} is present, reproducing the common case of
 * JNA arriving transitively (Testcontainers, docker-java, ...) on its own. Cases that need the
 * optional library run in a class loader assembled from the {@code optionalWatcher} configuration.
 * Those use reflection only to cross the class loader boundary; every call is to a public method.</p>
 */
@RestoreSystemProperties
class DirectoryWatcherSpec extends Specification {

    private static final String MAC_OS = 'Mac OS X'

    /**
     * Long enough that a watcher which only wakes on this interval cannot report inside the timeouts
     * used here, so a report proves the watcher is event driven rather than polling.
     */
    private static final long NON_POLLING_SLEEP_TIME = 120_000

    @TempDir
    Path watchedDirectory

    void 'a change to a watched file is reported'() {
        given:
        List<String> changed = watchForChanges(new DirectoryWatcher(), 1000)

        when:
        modifyWatchedFile()

        then:
        new PollingConditions(timeout: 60).eventually {
            assert changed.contains('watched.txt')
        }
    }

    void 'a change is reported without waiting for a poll interval'() {
        given: 'a macOS classpath carrying JNA but not the optional watcher library'
        System.setProperty('os.name', MAC_OS)

        and: 'a sleep interval far longer than the timeout below'
        List<String> changed = watchForChanges(new DirectoryWatcher(), NON_POLLING_SLEEP_TIME)

        when:
        modifyWatchedFile()

        then: 'JNA alone must not downgrade the watcher to polling'
        new PollingConditions(timeout: 40).eventually {
            assert changed.contains('watched.txt')
        }
    }

    // Needs a real macOS host, not just os.name: registering a directory calls through to the Carbon
    // framework, which JNA can only bind there. The linkage test below stays cross-platform because it
    // falls back before any native registration happens.
    @Requires({ os.macOs && DirectoryWatcherSpec.optionalWatcherJars() })
    void 'a change is reported when the optional native library is available'() {
        given:
        System.setProperty('os.name', MAC_OS)
        List<String> changed = watchForChangesIn(isolatedLoader(true), 1000)

        when:
        modifyWatchedFile()

        then:
        new PollingConditions(timeout: 60).eventually {
            assert changed.contains('watched.txt')
        }
    }

    @Requires({ DirectoryWatcherSpec.optionalWatcherJars() })
    void 'a change is reported when the optional library is present but cannot link'() {
        given: 'directory-watcher present without the JNA it links against, so loading it fails'
        System.setProperty('os.name', MAC_OS)

        when: 'the watcher is constructed and started'
        List<String> changed = watchForChangesIn(isolatedLoader(false), 1000)
        modifyWatchedFile()

        then: 'the linkage failure degrades to a working watcher rather than failing construction'
        new PollingConditions(timeout: 60).eventually {
            assert changed.contains('watched.txt')
        }
    }

    void 'macOS without the optional library says how to restore event driven watching'() {
        given: 'the classpath an application has by default, where the optional library is absent'
        System.setProperty('os.name', MAC_OS)

        when:
        String logged = captureStandardError { registerForCleanup(new DirectoryWatcher()) }

        then: 'the fallback is announced, not silent, and names the dependency that resolves it'
        logged.contains('WARN')
        logged.contains('io.methvin:directory-watcher')
    }

    void 'a platform with a native WatchService says nothing about the optional library'() {
        given: 'a platform whose JDK WatchService is already event driven'
        System.setProperty('os.name', 'Linux')

        when:
        String logged = captureStandardError { registerForCleanup(new DirectoryWatcher()) }

        then: 'the advice is macOS only, so it must not reach anyone it cannot help'
        !logged.contains('io.methvin:directory-watcher')
    }

    /**
     * Captures what an application would see on the console. slf4j-simple resolves
     * {@code System.err} on each write, so replacing it here redirects the watcher's own logging.
     */
    private static String captureStandardError(Closure<?> work) {
        PrintStream original = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))
        try {
            work.call()
        }
        finally {
            System.setErr(original)
        }
        captured.toString()
    }

    private File modifyWatchedFile() {
        File watched = new File(watchedDirectory.toFile(), 'watched.txt')
        watched.text = "modified ${System.nanoTime()}"
        watched
    }

    /**
     * Starts the watcher on the temporary directory and collects the names of files reported.
     */
    private List<String> watchForChanges(DirectoryWatcher watcher, long sleepTime) {
        File watched = new File(watchedDirectory.toFile(), 'watched.txt')
        watched.text = 'initial'

        List<String> changed = new CopyOnWriteArrayList<>()
        watcher.sleepTime = sleepTime
        watcher.addListener(new DirectoryWatcher.FileChangeListener() {
            void onChange(File file) { changed << file.name }

            void onNew(File file) { changed << file.name }
        })
        watcher.addWatchDirectory(watchedDirectory.toFile(), 'txt')
        watcher.start()
        registerForCleanup(watcher)
        // let the watcher register before the file is touched
        Thread.sleep(1000)
        changed
    }

    /**
     * The same as {@link #watchForChanges}, for a watcher loaded by another class loader. Reflection
     * bridges the loader boundary only; every member used is part of the public API.
     */
    private List<String> watchForChangesIn(ClassLoader loader, long sleepTime) {
        File watched = new File(watchedDirectory.toFile(), 'watched.txt')
        watched.text = 'initial'

        Class<?> watcherClass = loader.loadClass(DirectoryWatcher.name)
        Class<?> listenerClass = loader.loadClass(DirectoryWatcher.FileChangeListener.name)
        Object watcher = watcherClass.getDeclaredConstructor().newInstance()

        List<String> changed = new CopyOnWriteArrayList<>()
        Object listener = Proxy.newProxyInstance(loader, [listenerClass] as Class[], { proxy, method, arguments ->
            if (method.name in ['onChange', 'onNew']) {
                changed << ((File) arguments[0]).name
            }
            null
        } as InvocationHandler)

        watcherClass.getMethod('setSleepTime', long).invoke(watcher, sleepTime)
        watcherClass.getMethod('addListener', listenerClass).invoke(watcher, listener)
        watcherClass.getMethod('addWatchDirectory', File, String).invoke(watcher, watchedDirectory.toFile(), 'txt')
        watcherClass.getMethod('start').invoke(watcher)
        registerForCleanup(watcher, watcherClass.getMethod('setActive', boolean))
        Thread.sleep(1000)
        changed
    }

    /**
     * Builds a class loader carrying the optional watcher library, with JNA either present or absent.
     * The platform loader as parent keeps this JVM's application classpath out of the picture.
     */
    private static ClassLoader isolatedLoader(boolean includeJna) {
        List<File> entries = System.getProperty('java.class.path')
                .split(File.pathSeparator)
                .collect { new File(it) }
        // directory-watcher depends on JNA, so the exclusion has to cover the optional jars too
        entries.addAll(optionalWatcherJars())
        URL[] urls = entries
                .findAll { includeJna || !(it.name ==~ /jna(-platform)?-\d.*\.jar/) }
                .collect { it.toURI().toURL() }
        new URLClassLoader(urls, ClassLoader.platformClassLoader)
    }

    static List<File> optionalWatcherJars() {
        String path = System.getProperty('grails.test.optionalWatcherClasspath')
        path ? path.split(File.pathSeparator).collect { new File(it) }.findAll { it.exists() } : []
    }

    private final List<Closure> cleanupTasks = []

    private void registerForCleanup(Object watcher, java.lang.reflect.Method setActive = null) {
        cleanupTasks << {
            if (setActive) {
                setActive.invoke(watcher, false)
            }
            else {
                ((DirectoryWatcher) watcher).active = false
            }
        }
    }

    void cleanup() {
        cleanupTasks.each { it.call() }
    }
}
