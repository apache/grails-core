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
package grails.util

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

import grails.core.GrailsApplication
import spock.lang.Specification

class HoldersSpec extends Specification {

    void cleanup() {
        Holders.clear()
    }

    def "restores the prior fallback when its exact owner fails"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication expected = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication capturedPrevious = Holders.replaceGrailsApplication(expected)

        when:
        boolean restored = Holders.restoreGrailsApplicationAfterFailure(expected, capturedPrevious)

        then:
        restored
        Holders.findApplication().is(previous)
    }

    def "restoring a visible successor skips its failed captured fallback"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication failed = Mock()
        GrailsApplication successor = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication failedPrevious = Holders.replaceGrailsApplication(failed)
        GrailsApplication successorPrevious = Holders.replaceGrailsApplication(successor)

        when: "the owner fails after a successor has become visible"
        boolean restoredFailed = Holders.restoreGrailsApplicationAfterFailure(failed, failedPrevious)
        boolean restoredSuccessor = Holders.restoreGrailsApplication(successor, successorPrevious)

        then:
        !restoredFailed
        restoredSuccessor
        Holders.findApplication().is(previous)
    }

    def "a later failure skips an already failed fallback"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication first = Mock()
        GrailsApplication second = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication firstPrevious = Holders.replaceGrailsApplication(first)
        GrailsApplication secondPrevious = Holders.replaceGrailsApplication(second)

        when:
        boolean restoredFirst = Holders.restoreGrailsApplicationAfterFailure(first, firstPrevious)
        boolean restoredSecond = Holders.restoreGrailsApplicationAfterFailure(second, secondPrevious)

        then:
        !restoredFirst
        restoredSecond
        Holders.findApplication().is(previous)
    }

    def "failures in reverse order restore each owner to the nonfailed predecessor"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication first = Mock()
        GrailsApplication second = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication firstPrevious = Holders.replaceGrailsApplication(first)
        GrailsApplication secondPrevious = Holders.replaceGrailsApplication(second)

        when:
        boolean restoredSecond = Holders.restoreGrailsApplicationAfterFailure(second, secondPrevious)
        boolean restoredFirst = Holders.restoreGrailsApplicationAfterFailure(first, firstPrevious)

        then:
        restoredSecond
        restoredFirst
        Holders.findApplication().is(previous)
    }

    def "set and replace cannot resurrect a failed application"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication failed = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication failedPrevious = Holders.replaceGrailsApplication(failed)
        Holders.restoreGrailsApplicationAfterFailure(failed, failedPrevious)

        when:
        Holders.setGrailsApplication(failed)
        GrailsApplication replaced = Holders.replaceGrailsApplication(failed)

        then:
        replaced.is(previous)
        Holders.findApplication().is(previous)
    }

    def "ordinary replacement and restoration retain exact identity-CAS semantics"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication replacement = Mock()
        GrailsApplication other = Mock()
        Holders.setGrailsApplication(previous)

        when:
        GrailsApplication replaced = Holders.replaceGrailsApplication(replacement)
        boolean restoredWithOtherOwner = Holders.restoreGrailsApplication(other, previous)
        boolean restoredWithExactOwner = Holders.restoreGrailsApplication(replacement, previous)

        then:
        replaced.is(previous)
        !restoredWithOtherOwner
        restoredWithExactOwner
        Holders.findApplication().is(previous)
    }

    def "clear removes fallback and failure tombstones"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication failed = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication failedPrevious = Holders.replaceGrailsApplication(failed)
        Holders.restoreGrailsApplicationAfterFailure(failed, failedPrevious)

        when:
        Holders.clear()
        GrailsApplication cleared = Holders.findApplication()
        Holders.setGrailsApplication(failed)

        then:
        cleared == null
        Holders.findApplication().is(failed)
    }

    def "reset removes fallback and failure tombstones"() {
        given:
        GrailsApplication previous = Mock()
        GrailsApplication failed = Mock()
        Holders.setGrailsApplication(previous)
        GrailsApplication failedPrevious = Holders.replaceGrailsApplication(failed)
        Holders.restoreGrailsApplicationAfterFailure(failed, failedPrevious)

        when:
        Holders.reset()
        GrailsApplication reset = Holders.findApplication()
        Holders.setGrailsApplication(failed)

        then:
        reset == null
        Holders.findApplication().is(failed)
    }

    def "findApplication drains queued failed fallback tombstones"() {
        given:
        FailedReferences references = createFailedTombstoneWithVisibleCurrentApplication()

        when: "the failed application has no remaining strong test reference"
        Reference<?> failed = awaitCollection(references.failedQueue)

        then:
        failed?.is(references.failed)

        when: "a steady-state fallback read drains the queued tombstone"
        GrailsApplication current = Holders.findApplication()
        Reference<?> previous = awaitCollection(references.previousQueue)

        then:
        current != null
        previous?.is(references.previous)
    }

    private static FailedReferences createFailedTombstoneWithVisibleCurrentApplication() {
        Holders.clear()
        GrailsApplication previous = application()
        GrailsApplication failed = application()
        Holders.setGrailsApplication(previous)
        GrailsApplication failedPrevious = Holders.replaceGrailsApplication(failed)
        Holders.setGrailsApplication(application())
        assert !Holders.restoreGrailsApplicationAfterFailure(failed, failedPrevious)
        new FailedReferences(failed, previous)
    }

    private static GrailsApplication application() {
        java.lang.reflect.Proxy.newProxyInstance(GrailsApplication.classLoader, [GrailsApplication] as Class[], { proxy, method, arguments ->
            if (method.name == 'equals') {
                return proxy.is(arguments[0])
            }
            if (method.name == 'hashCode') {
                return System.identityHashCode(proxy)
            }
            null
        } as java.lang.reflect.InvocationHandler) as GrailsApplication
    }

    private static Reference<?> awaitCollection(ReferenceQueue<GrailsApplication> queue) {
        for (int attempt = 0; attempt < 50; attempt++) {
            System.gc()
            System.runFinalization()
            System.identityHashCode(new byte[1024 * 1024])
            Reference<?> reference = queue.poll()
            if (reference != null) {
                return reference
            }
        }
        null
    }

    private static class FailedReferences {
        final ReferenceQueue<GrailsApplication> failedQueue = new ReferenceQueue<>()
        final ReferenceQueue<GrailsApplication> previousQueue = new ReferenceQueue<>()
        final WeakReference<GrailsApplication> failed
        final WeakReference<GrailsApplication> previous

        FailedReferences(GrailsApplication failed, GrailsApplication previous) {
            this.failed = new WeakReference<>(failed, failedQueue)
            this.previous = new WeakReference<>(previous, previousQueue)
        }
    }
}
