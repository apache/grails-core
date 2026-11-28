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
package org.apache.grails.buildsrc

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout

class TestRepositoryMarkerPluginSpec extends Specification {

    @TempDir
    File repository

    File marker

    void setup() {
        marker = TestRepositoryMarker.file(repository, TestRepositoryMarker.FRAMEWORK)
        marker.parentFile.mkdirs()
    }

    void "a publish removes the record of the last complete publication"() {
        given:
        marker.text = 'version=9.0.0-SNAPSHOT\n'

        when:
        new TestRepositoryMarkerPlugin.InvalidateMarker(marker).execute(null)

        then:
        !marker.exists()
    }

    void "a publish when there is no record does nothing"() {
        when:
        new TestRepositoryMarkerPlugin.InvalidateMarker(marker).execute(null)

        then:
        noExceptionThrown()
        !marker.exists()
    }

    @Timeout(60)
    void "publish tasks that run at the same time all remove the record, and none fails because another removed it first"() {
        given: 'the publish tasks of a parallel build, each with the action, started together for every record'
        int tasks = 8
        int rounds = 200
        ExecutorService pool = Executors.newFixedThreadPool(tasks)
        List<Throwable> failures = Collections.synchronizedList([])

        when:
        try {
            rounds.times {
                marker.text = 'version=9.0.0-SNAPSHOT\n'
                CountDownLatch start = new CountDownLatch(1)
                List<Future<Object>> running = (1..tasks).collect {
                    pool.submit({
                        start.await()
                        try {
                            new TestRepositoryMarkerPlugin.InvalidateMarker(marker).execute(null)
                        } catch (Throwable failure) {
                            failures << failure
                        }
                        null
                    } as Callable<Object>)
                }
                start.countDown()
                running*.get(30, TimeUnit.SECONDS)
            }
        } finally {
            pool.shutdownNow()
        }

        then:
        failures.isEmpty()
        !marker.exists()
    }

    void "a record that cannot be removed fails the publish, with the reason"() {
        given: 'a directory that is not empty where the record is'
        new File(marker, 'held').with {
            parentFile.mkdirs()
            text = 'held'
        }

        when:
        new TestRepositoryMarkerPlugin.InvalidateMarker(marker).execute(null)

        then:
        GradleException e = thrown()
        e.message == "Cannot invalidate test repository record ${marker}"
        e.cause instanceof IOException
    }
}
