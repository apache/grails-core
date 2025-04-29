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
package grails.plugin.geb

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode
import static org.testcontainers.containers.VncRecordingContainer.VncRecordingFormat

/**
 * Handles parsing various recording configuration used by {@link GrailsContainerGebExtension}
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class GrailsGebSettings {

    private static VncRecordingMode DEFAULT_RECORDING_MODE = VncRecordingMode.SKIP
    private static VncRecordingFormat DEFAULT_RECORDING_FORMAT = VncRecordingFormat.MP4
    private static int DEFAULT_TIMEOUT_IMPLICITLY_WAIT = 0
    private static int DEFAULT_TIMEOUT_PAGE_LOAD = 300
    private static int DEFAULT_TIMEOUT_SCRIPT = 30

    String tracingEnabled
    String recordingDirectoryName
    String reportingDirectoryName
    VncRecordingMode recordingMode
    VncRecordingFormat recordingFormat
    LocalDateTime startTime
    int implicitlyWait
    int pageLoadTimeout
    int scriptTimeout

    GrailsGebSettings(LocalDateTime startTime) {
        tracingEnabled = System.getProperty('grails.geb.tracing.enabled', 'false')
        recordingDirectoryName = System.getProperty('grails.geb.recording.directory', 'build/gebContainer/recordings')
        reportingDirectoryName = System.getProperty('grails.geb.reporting.directory', 'build/gebContainer/reports')
        recordingMode = VncRecordingMode.valueOf(
                System.getProperty('grails.geb.recording.mode', DEFAULT_RECORDING_MODE.name())
        )
        recordingFormat = VncRecordingFormat.valueOf(
                System.getProperty('grails.geb.recording.format', DEFAULT_RECORDING_FORMAT.name())
        )
        implicitlyWait = getIntProperty('grails.geb.timeouts.implicitlyWait', DEFAULT_TIMEOUT_IMPLICITLY_WAIT)
        pageLoadTimeout = getIntProperty('grails.geb.timeouts.pageLoad', DEFAULT_TIMEOUT_PAGE_LOAD)
        scriptTimeout = getIntProperty('grails.geb.timeouts.script', DEFAULT_TIMEOUT_SCRIPT)
        this.startTime = startTime
    }

    private static int getIntProperty(String propertyName, int defaultValue) {
        Integer.getInteger(propertyName, defaultValue) ?: defaultValue
    }

    boolean isRecordingEnabled() {
        recordingMode != VncRecordingMode.SKIP
    }

    @Memoized
    File getRecordingDirectory() {
        if (!recordingEnabled) {
            return null
        }

        File recordingDirectory = new File("${recordingDirectoryName}${File.separator}${DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss').format(startTime)}")
        if (!recordingDirectory.exists()) {
            if (!recordingDirectory.parentFile.exists()) {
                log.info('Could not find `{}` Directory for recording. Creating...', recordingDirectoryName)
            }
            recordingDirectory.mkdirs()
        } else if (!recordingDirectory.isDirectory()) {
            throw new IllegalStateException("Configured recording directory '${recordingDirectory}' is expected to be a directory, but found file instead.")
        }

        return recordingDirectory
    }

    @Memoized
    File getReportingDirectory() {
        if (!reportingDirectoryName) {
            return null
        }

        File reportingDirectory = new File("${reportingDirectoryName}${File.separator}${DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss').format(startTime)}")
        if (!reportingDirectory.exists()) {
            if (!reportingDirectory.parentFile.exists()) {
                log.info('Could not find `{}` Directory for reporting. Creating...', reportingDirectoryName)
            }
            reportingDirectory.mkdirs()
        } else if (!reportingDirectory.isDirectory()) {
            throw new IllegalStateException("Configured reporting directory '${reportingDirectory}' is expected to be a directory, but found file instead.")
        }

        return reportingDirectory
    }
}
