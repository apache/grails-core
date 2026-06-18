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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

import geb.waiting.Wait

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode
import static org.testcontainers.containers.VncRecordingContainer.VncRecordingFormat

/**
 * Handles parsing various recording configuration
 * used by {@link GrailsContainerGebExtension}.
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class GrailsGebSettings {

    public static boolean DEFAULT_AT_CHECK_WAITING = false
    private static VncRecordingMode DEFAULT_RECORDING_MODE = VncRecordingMode.SKIP
    private static VncRecordingFormat DEFAULT_RECORDING_FORMAT = VncRecordingFormat.MP4
    public static int DEFAULT_TIMEOUT_IMPLICITLY_WAIT = 0
    public static int DEFAULT_TIMEOUT_PAGE_LOAD = 300
    public static int DEFAULT_TIMEOUT_SCRIPT = 30

    boolean tracingEnabled
    String recordingDirectoryName
    String reportingDirectoryName
    boolean restartRecordingContainerPerTest
    VncRecordingMode recordingMode
    VncRecordingFormat recordingFormat
    LocalDateTime startTime
    int implicitlyWait
    int pageLoadTimeout
    int scriptTimeout
    /**
     * A template for the Docker image to use for Geb containers. 
     * Supports ${browser} and ${version} placeholders. 
     * Useful for parameterizing the image based on architecture (e.g. ARM64).
     */
    String containerImageTemplate

    boolean atCheckWaiting
    Number timeout
    Number retryInterval

    GrailsGebSettings(LocalDateTime startTime) {
        tracingEnabled = getBooleanProperty('grails.geb.tracing.enabled', false)
        recordingDirectoryName = System.getProperty('grails.geb.recording.directory', 'build/gebContainer/recordings')
        reportingDirectoryName = System.getProperty('grails.geb.reporting.directory', 'build/gebContainer/reports')
        containerImageTemplate = System.getProperty('grails.geb.container.image.template')
        recordingMode = VncRecordingMode.valueOf(
                System.getProperty('grails.geb.recording.mode', DEFAULT_RECORDING_MODE.name())
        )
        recordingFormat = VncRecordingFormat.valueOf(
                System.getProperty('grails.geb.recording.format', DEFAULT_RECORDING_FORMAT.name())
        )
        restartRecordingContainerPerTest = getBooleanProperty(
                'grails.geb.recording.restartRecordingContainerPerTest',
                true
        )
        implicitlyWait = getIntProperty('grails.geb.timeouts.implicitlyWait', DEFAULT_TIMEOUT_IMPLICITLY_WAIT)
        pageLoadTimeout = getIntProperty('grails.geb.timeouts.pageLoad', DEFAULT_TIMEOUT_PAGE_LOAD)
        scriptTimeout = getIntProperty('grails.geb.timeouts.script', DEFAULT_TIMEOUT_SCRIPT)
        atCheckWaiting = getBooleanProperty('grails.geb.atCheckWaiting.enabled', DEFAULT_AT_CHECK_WAITING)
        timeout = getNumberProperty('grails.geb.timeouts.timeout', Wait.DEFAULT_TIMEOUT)
        retryInterval = getNumberProperty('grails.geb.timeouts.retryInterval', Wait.DEFAULT_RETRY_INTERVAL)
        this.startTime = startTime
    }

    private static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        Boolean.parseBoolean(System.getProperty(propertyName, defaultValue.toString()))
    }

    private static int getIntProperty(String propertyName, int defaultValue) {
        Integer.getInteger(propertyName, defaultValue) ?: defaultValue
    }

    private static Number getNumberProperty(String propertyName, Number defaultValue) {
        def propValue = System.getProperty(propertyName)
        if (propValue) {
            try {
                if (propValue.contains('.')) {
                    return new BigDecimal(propValue)
                } else {
                    return Integer.parseInt(propValue)
                }
            } catch (NumberFormatException ignored) {
                log.warn(
                        'Could not parse property [{}] with value [{}] as a Number. Using default value [{}] instead.',
                        propertyName,
                        propValue,
                        defaultValue
                )
            }
        }
        return defaultValue
    }

    boolean isRecordingEnabled() {
        recordingMode != VncRecordingMode.SKIP
    }

    @Memoized
    File getRecordingDirectory() {
        if (!recordingEnabled) {
            return null
        }
        createDirectory(recordingDirectoryName, 'recording')
    }

    @Memoized
    File getReportingDirectory() {
        if (!reportingDirectoryName) {
            return null
        }
        createDirectory(reportingDirectoryName, 'reporting')
    }

    private File createDirectory(String directoryName, String useCase) {
        def dir = new File(
                "$directoryName$File.separator${DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss').format(startTime)}"
        )
        if (!dir.exists()) {
            if (!dir.parentFile.exists()) {
                log.info('Could not find [{}] directory for {}. Creating...', directoryName, useCase)
            }
            dir.mkdirs()
        } else if (!dir.isDirectory()) {
            throw new IllegalStateException(
                    "Configured $useCase directory [$dir] is expected to be a directory, but found file instead."
            )
        }
        return dir
    }
}
