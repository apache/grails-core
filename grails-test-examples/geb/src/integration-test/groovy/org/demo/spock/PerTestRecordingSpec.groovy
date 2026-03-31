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

import java.nio.file.Files

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

        when: 'getting the most recent recording directory'
        // Find the timestamped recording directory (should be the most recent one)
        File recordingDir = null
        def timestampedDirs = baseRecordingDir.listFiles({ File dir ->
            dir.isDirectory() && dir.name ==~ /^\d{8}_\d{6}$/
        } as FileFilter)

        if (timestampedDirs) {
            // Get the most recent directory
            recordingDir = timestampedDirs.sort { it.name }.last()
        }

        then: 'the recording directory should be found'
        recordingDir != null

        when: 'getting all video recording files (mp4 or flv) from the recording directory'
        def minFileCount = 2 // At least 2 files for the first two test methods
        def recordingFiles = waitForRecordingFiles(recordingDir, this.class.simpleName, minFileCount)
        def names = recordingFiles*.name.join('\n')

        then: 'recording files should exist for each test method'
        recordingFiles.length >= minFileCount
        names.contains('setup_running_a_test_to_create_a_recording')
        names.contains('setup_running_a_second_test_to_create_another')

        and: 'the recording files should have different content'
        // Sort by last modified time to get the most recent files
        def sortedFiles = recordingFiles.sort { it.lastModified() }
        def secondLastFile = sortedFiles[sortedFiles.length - 2]
        def lastFile = sortedFiles[sortedFiles.length - 1]
        Files.mismatch(lastFile.toPath(), secondLastFile.toPath()) != -1
    }

    private static boolean isVideoFile(File file) {
        file.isFile() && (file.name.endsWith('.mp4') || file.name.endsWith('.flv'))
    }

    private static File[] waitForRecordingFiles(
            File recordingDir,
            String testClassName,
            int minFileCount = 2,
            long timeoutMillis = 10_000L,
            long pollIntervalMillis = 500L
    ) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        File[] recordingFiles = []
        while (System.currentTimeMillis() < deadline) {
            recordingFiles = recordingDir.listFiles({ file ->
                isVideoFile(file) && file.name.contains(testClassName)
            } as FileFilter) ?: [] as File[]

            if (recordingFiles.length >= minFileCount) {
                break
            }
            sleep(pollIntervalMillis)
        }
        return recordingFiles
    }
}
