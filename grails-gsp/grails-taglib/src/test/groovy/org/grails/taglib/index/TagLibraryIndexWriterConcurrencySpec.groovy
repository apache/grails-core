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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Every tag library compiled into one directory adds itself to a manifest they all share, which is a
 * read, a change and a write back.
 *
 * <p>Two compilations writing to the same directory at once - joint compilation, or parallel tasks
 * sharing an output - would interleave without guarding, and a writer would put back a copy that never
 * saw another's entry. Losing an entry is silent: the descriptor exists, nothing names it, so the tag
 * library is simply never discovered and its tags resolve dynamically for evermore.
 */
class TagLibraryIndexWriterConcurrencySpec extends Specification {

    @TempDir
    Path tempDir

    void 'every tag library written at once is named in the manifest'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('out')).toFile()
        int writers = 32
        ExecutorService pool = Executors.newFixedThreadPool(8)
        CountDownLatch start = new CountDownLatch(1)
        CountDownLatch done = new CountDownLatch(writers)

        when: 'they all write into the same directory, released together to maximise overlap'
        List<Throwable> failures = Collections.synchronizedList([])
        (0..<writers).each { int i ->
            pool.submit {
                try {
                    start.await()
                    TagLibraryIndexWriter.write(destination, "demo.TagLib${i}".toString(), 'demo',
                            ["tag${i}".toString()])
                }
                catch (Throwable t) {
                    failures << t
                }
                finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await(60, TimeUnit.SECONDS)
        pool.shutdown()

        and: 'nothing failed on the way'
        assert failures.isEmpty(), failures.collect { it.toString() }.join('; ')

        then: 'the manifest names all of them, not just whichever wrote last'
        Properties manifest = manifestIn(destination)
        manifest.stringPropertyNames() == (0..<writers).collect { "demo.TagLib${it}".toString() } as Set

        and: 'and every descriptor it names is there to be read'
        manifest.stringPropertyNames().every {
            new File(destination, "META-INF/grails/taglibs/${it}.properties").isFile()
        }
    }

    private static Properties manifestIn(File destination) {
        Properties manifest = new Properties()
        new File(destination, 'META-INF/grails/taglibs/index.properties').withInputStream {
            manifest.load(it)
        }
        manifest
    }

    void 'the index reads back every tag written concurrently'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('readback')).toFile()
        int writers = 16
        ExecutorService pool = Executors.newFixedThreadPool(8)
        CountDownLatch done = new CountDownLatch(writers)

        when:
        (0..<writers).each { int i ->
            pool.submit {
                try {
                    TagLibraryIndexWriter.write(destination, "demo.Read${i}".toString(), 'readback',
                            ["tag${i}".toString()])
                }
                finally {
                    done.countDown()
                }
            }
        }
        done.await(60, TimeUnit.SECONDS)
        pool.shutdown()

        and:
        URLClassLoader loader = new URLClassLoader([destination.toURI().toURL()] as URL[], (ClassLoader) null)
        TagLibraryIndex index = TagLibraryIndex.load(loader)

        then: 'which is what a lost manifest entry would silently take away'
        index.getTagNames('readback') == (0..<writers).collect { "tag${it}".toString() } as Set

        cleanup:
        loader?.close()
    }
}
