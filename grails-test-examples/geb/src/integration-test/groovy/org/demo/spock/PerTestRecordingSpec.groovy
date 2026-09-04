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

package org.demo.spock

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import spock.lang.Stepwise

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

import org.demo.spock.pages.HomePage
import org.demo.spock.pages.UploadPage

@Stepwise
@Integration
class PerTestRecordingSpec extends ContainerGebSpec {

    void '(setup) running a test to create a recording'() {
        when: 'visiting the home page'
        to(HomePage)

        then: 'the page loads correctly'
        title == 'Welcome to Grails'
    }

    void '(setup) running a second test to create another recording'() {
        when: 'visiting another page than the previous test'
        to(UploadPage)

        and: 'pausing so we can see the page change in the video'
        Thread.sleep(500)

        then: 'the page loads correctly'
        title == 'Upload Test'
    }

    void 'the recordings of the previous two tests are different'() {
        when: 'getting the configured base recording directory'
        // Logic from GrailsGebSettings
        def recordingDirectoryName = System.getProperty(
                'grails.geb.recording.directory',
                'build/gebContainer/recordings'
        )
        def baseRecordingDir = new File(recordingDirectoryName)

        then: 'the base recording directory exists'
        baseRecordingDir.exists()

        when: 'collecting the recordings this spec produced during this test run'
        // The timestamped recording directory is created per JVM when the Geb
        // extension starts, but the base directory accumulates directories from
        // earlier runs and from sibling test JVMs. Selecting "the most recent"
        // directory races with those, so instead every directory stamped after
        // this JVM started is searched for recordings named after this spec —
        // a spec runs in exactly one JVM, so the match is unambiguous.
        def minFileCount = 2 // At least 2 files for the first two test methods
        def recordingFiles = waitForRecordingFiles(baseRecordingDir, this.class.simpleName, minFileCount)
        def names = recordingFiles*.name.join('\n')

        then: 'recording files should exist for each test method'
        recordingFiles.size() >= minFileCount
        names.contains('setup_running_a_test_to_create_a_recording')
        names.contains('setup_running_a_second_test_to_create_another')

        and: 'the recording files should have different content'
        def firstRecording = recordingFiles.find { it.name.contains('setup_running_a_test_to_create_a_recording') }
        def secondRecording = recordingFiles.find { it.name.contains('setup_running_a_second_test_to_create_another') }
        Files.mismatch(firstRecording.toPath(), secondRecording.toPath()) != -1
    }

    private static final DateTimeFormatter RECORDING_DIR_FORMAT = DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss')

    private static final LocalDateTime JVM_START = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(ManagementFactory.runtimeMXBean.startTime),
            ZoneId.systemDefault()
    ).withNano(0)

    private static boolean isVideoFile(File file) {
        file.isFile() && (file.name.endsWith('.mp4') || file.name.endsWith('.flv'))
    }

    /**
     * The timestamped recording directories created by this test run — those
     * whose directory-name timestamp is not before this JVM's start time.
     * Directories left behind by previous runs are always older and excluded.
     */
    private static List<File> currentRunRecordingDirs(File baseRecordingDir) {
        (baseRecordingDir.listFiles({ File dir ->
            dir.isDirectory() && dir.name ==~ /^\d{8}_\d{6}$/
        } as FileFilter) ?: new File[0]).findAll { File dir ->
            !LocalDateTime.parse(dir.name, RECORDING_DIR_FORMAT).isBefore(JVM_START)
        }
    }

    private static List<File> waitForRecordingFiles(
            File baseRecordingDir,
            String testClassName,
            int minFileCount = 2,
            long timeoutMillis = 10_000L,
            long pollIntervalMillis = 500L
    ) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        List<File> recordingFiles = []
        while (System.currentTimeMillis() < deadline) {
            // Re-scan on every poll: the directory and the files both appear
            // asynchronously while the recording container flushes videos.
            recordingFiles = currentRunRecordingDirs(baseRecordingDir).collectMany { File dir ->
                (dir.listFiles({ File file ->
                    isVideoFile(file) && file.name.contains(testClassName)
                } as FileFilter) ?: new File[0]) as List<File>
            }

            if (recordingFiles.size() >= minFileCount) {
                break
            }
            sleep(pollIntervalMillis)
        }
        return recordingFiles
    }
}
