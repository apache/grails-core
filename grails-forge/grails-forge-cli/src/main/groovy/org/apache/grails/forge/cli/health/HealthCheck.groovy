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
package org.apache.grails.forge.cli.health

import groovy.transform.CompileStatic
import org.grails.forge.util.VersionInfo

/**
 * One check of {@code project health}. A check reads what it needs of the project through the
 * {@link HealthContext}, as text and within bounds, and runs nothing. It gives one finding or more:
 * what it found, or one {@link HealthLevel#OK} finding when it found nothing, or one
 * {@link HealthLevel#SKIPPED} finding with the reason when it cannot decide. A check that cannot
 * decide never says that it found nothing.
 *
 * @since 9.0
 */
@CompileStatic
abstract class HealthCheck {

    // The upgrade notes a finding names, by the anchors the notes have in the guide: a number moves from one release to the next
    static final String NOTE_UPGRADE = 'upgrading90x'
    static final String NOTE_GROOVY = 'upgradingGroovy6'
    static final String NOTE_GPARS = 'upgradingGpars'
    static final String NOTE_RXJAVA = 'upgradingRxJavaModules'
    static final String NOTE_JLINE = 'upgradingJLine4'
    static final String NOTE_NO_PROFILE_DEPENDENCY = 'upgradingNoProfileDependency'
    static final String NOTE_NO_PROFILE = 'upgradingNoProfileAtRunTime'
    static final String NOTE_ONE_COMMAND_LINE = 'upgradingOneCommandLine'
    static final String NOTE_SHELL_NOT_PUBLISHED = 'upgradingShellNotPublished'
    static final String NOTE_GRADLE_WRAPPER = 'upgradingGradleWrapper'
    static final String NOTE_GRADLE_CHECKSUM = 'upgradingGradleChecksum'
    static final String NOTE_RECORD = 'upgradingProjectRecord'
    static final String NOTE_RECORD_IGNORE = 'upgradingRecordIgnoresMachineState'
    static final String NOTE_COMMAND_CATALOG = 'upgradingCommandCatalog'
    static final String NOTE_SETTINGS_GROOVY = 'upgradingSettingsGroovy'
    static final String NOTE_TOOLS_RENAMED = 'upgradingToolsRenamed'
    static final String NOTE_TOOLS_GRAILS_7 = 'upgradingLegacyBridgeRemoved'

    /**
     * @return the name of the check, which is the first part of the id of each of its findings
     */
    abstract String getName()

    /**
     * @param context what the check reads the project through
     * @return what the check found: at least one finding
     */
    abstract List<HealthFinding> check(HealthContext context)

    /**
     * @return a finding of this check
     */
    protected HealthFinding found(String finding, HealthLevel level, String message, String fix, String command, List<String> files, String note) {
        return new HealthFinding(getName(), finding, level, message, fix, command, files, note == null ? null : noteAddress(note))
    }

    /**
     * @return the one finding of a check that found nothing
     */
    protected HealthFinding ok(String message, List<String> files = []) {
        return new HealthFinding(getName(), HealthFinding.OK, HealthLevel.OK, message, null, null, files, null)
    }

    /**
     * @param reason why the check cannot decide, as one line
     * @return the one finding of a check that cannot decide
     */
    protected HealthFinding skipped(String reason, List<String> files = []) {
        return new HealthFinding(getName(), HealthFinding.SKIPPED, HealthLevel.SKIPPED, reason, null, null, files, null)
    }

    /**
     * @param anchor the anchor of an upgrade note in the guide
     * @return where the note is, in the guide of the version of this command line
     */
    static String noteAddress(String anchor) {
        return "https://grails.apache.org/docs/${VersionInfo.getDocumentationVersion()}/guide/upgrading.html#${anchor}".toString()
    }
}
