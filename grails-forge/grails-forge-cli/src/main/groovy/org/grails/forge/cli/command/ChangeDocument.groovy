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
package org.grails.forge.cli.command

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.FeatureRequestException
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.feature.FeatureSelectionException
import java.nio.file.Path

/**
 * The JSON document that {@code feature add --dry-run --json} and {@code project regenerate --diff
 * --json} write: what the command came to, as an outcome and a reason a tool reads, the paths of the
 * files as they are in the project, and the line a person reads. Every member is always there, in
 * the same order; one that does not apply is {@code null}.
 */
@CompileStatic
@PackageScope
final class ChangeDocument {

    /** The version of the document. It changes only when a reader of the earlier version would misread it; members may be added at the end without it changing */
    static final int FORMAT = 1

    /** The comparison was made, and there is nothing to change, or nothing that differs */
    static final String NO_CHANGES = 'no-changes'

    /** The comparison was made, and there are changes to make, or files that differ */
    static final String CHANGES = 'changes'

    /** The comparison was made, and files cannot be written without losing what the project has */
    static final String CONFLICTS = 'conflicts'

    /** What was asked cannot be done to this project, as it was asked: see the reason */
    static final String REFUSED = 'refused'

    /** The record of the project under .grails is missing, cannot be read, or does not give the project it records */
    static final String INVALID_RECORD = 'invalid-record'

    /** The command failed while it worked: a file cannot be read, or something went wrong that it does not know */
    static final String FAILED = 'failed'

    static final String NO_PROJECT = 'no-project'
    static final String UNTRUSTED_PROJECT = 'untrusted-project'
    static final String NO_RECORD = 'no-record'
    static final String UNREADABLE_RECORD = 'unreadable-record'
    static final String FEATURE_SELECTION = 'feature-selection'
    static final String CHANGES_OPTION = 'changes-option'
    static final String REMOVES_REQUESTED_FEATURE = 'removes-requested-feature'
    static final String ERROR = 'error'

    private static final Map<Action, String> ACTIONS = [(Action.ADD): 'add', (Action.UPDATE): 'update', (Action.MERGE): 'merge', (Action.ADOPT): 'keep',
                                                        (Action.CONFLICT): 'conflict', (Action.REMOVE): 'remove'] as Map<Action, String>

    // what a reason of the project change comes to; any other is a failure
    private static final Map<String, String> OUTCOMES = [
            (ProjectChangeException.UNTRACKED_RECORD): REFUSED,
            (ProjectChangeException.OTHER_GENERATOR): REFUSED,
            (ProjectChangeException.PACK_MISSING): REFUSED,
            (ProjectChangeException.PACK_DIFFERS): REFUSED,
            (ProjectChangeException.INTERRUPTED_CHANGE): REFUSED,
            (ProjectChangeException.NOT_REGENERABLE): INVALID_RECORD,
            (ProjectChangeException.BASELINE_DIFFERS): INVALID_RECORD,
            (ProjectChangeException.RECORD_DIFFERS): INVALID_RECORD,
            (ProjectChangeException.UNREADABLE_FILE): FAILED] as Map<String, String>

    private final Map<String, Object> members = new LinkedHashMap<>()

    /**
     * @param command the command, as it is typed
     * @param project the directory of the project, or {@code null} when there is none
     * @param others the members of the command that follow the common ones, in their order
     */
    private ChangeDocument(String command, Path project, List<String> others) {
        members.put('format', FORMAT)
        members.put('command', command)
        members.put('projectDirectory', project == null ? null : project.toString())
        members.put('outcome', null)
        members.put('reason', null)
        members.put('message', null)
        members.put('hasChanges', null)
        for (String name : others) {
            members.put(name, null)
        }
    }

    static ChangeDocument ofFeatureAdd(Path project) {
        ChangeDocument document = new ChangeDocument('feature add', project, ['applicable', 'requested', 'alreadyPresent', 'added', 'replaced', 'replacements',
                                                                              'lostFeatures', 'changedOptions', 'files', 'notes'])
        document.members.put('applicable', false)
        return document
    }

    static ChangeDocument ofRegenerate(Path project) {
        return new ChangeDocument('project regenerate', project, ['files'])
    }

    String getOutcome() {
        return (String) members.get('outcome')
    }

    /**
     * Ends the document of a directory that is in no project the command can use.
     */
    ChangeDocument noProject(String untrusted, String message) {
        return ended(REFUSED, untrusted == null ? NO_PROJECT : UNTRUSTED_PROJECT, message)
    }

    /**
     * Ends the document with an outcome that is not a comparison.
     */
    ChangeDocument ended(String outcome, String reason, String message) {
        members.put('outcome', outcome)
        members.put('reason', reason)
        members.put('message', message)
        return this
    }

    /**
     * Ends the document with what was thrown: the outcome and the reason it stands for.
     */
    ChangeDocument ended(Exception failure) {
        return ended(failure, failure.getMessage() ?: failure.getClass().getSimpleName())
    }

    /**
     * Ends the document with what was thrown, and what is said of it: the outcome and the reason the
     * failure stands for, and the message.
     */
    ChangeDocument ended(Exception failure, String message) {
        if (failure instanceof FeatureRequestException) {
            return ended(REFUSED, ((FeatureRequestException) failure).getReason(), message)
        }
        if (failure instanceof FeatureSelectionException) {
            return ended(REFUSED, FEATURE_SELECTION, message)
        }
        if (failure instanceof ProjectStateException) {
            return ended(INVALID_RECORD, UNREADABLE_RECORD, message)
        }
        if (failure instanceof ProjectChangeException && ((ProjectChangeException) failure).getReason() != null) {
            String reason = ((ProjectChangeException) failure).getReason()
            return ended(OUTCOMES.getOrDefault(reason, FAILED), reason, message)
        }
        return ended(FAILED, ERROR, message)
    }

    /**
     * Ends the document of {@code feature add} with what adding the features comes to.
     *
     * @param notes what the features said while the application was generated with them
     */
    ChangeDocument planned(ChangePlan plan, List<String> notes, String conflicts) {
        String outcome
        String reason = null
        String message = null
        if (plan.isNothingToAdd()) {
            outcome = NO_CHANGES
        } else if (plan.isRefused()) {
            outcome = REFUSED
            reason = plan.changedOptions.isEmpty() ? REMOVES_REQUESTED_FEATURE : CHANGES_OPTION
            message = plan.refusal
        } else if (!plan.getConflicts().isEmpty()) {
            outcome = CONFLICTS
            reason = CONFLICTS
            message = conflicts
        } else {
            outcome = CHANGES
        }
        ended(outcome, reason, message)
        members.put('hasChanges', !plan.isNothingToAdd())
        members.put('applicable', outcome == NO_CHANGES || outcome == CHANGES)
        members.put('requested', plan.requested)
        members.put('alreadyPresent', plan.alreadyThere)
        members.put('added', plan.added)
        members.put('replaced', plan.replaced)
        members.put('replacements', plan.replacements)
        members.put('lostFeatures', plan.lostFeatures)
        members.put('changedOptions', plan.changedOptions)
        members.put('files', plan.files.collect { PlannedFile file ->
            Map<String, Object> each = new LinkedHashMap<>()
            each.put('path', file.path)
            each.put('action', ACTIONS.get(file.action))
            each.put('reason', file.reason)
            each.put('details', file.details)
            each
        })
        members.put('notes', new ArrayList<String>(notes))
        return this
    }

    /**
     * Ends the document of {@code project regenerate} with the files that are not as generated.
     *
     * @param differences how each file differs, by its path in the project
     */
    ChangeDocument compared(Map<String, String> differences) {
        ended(differences.isEmpty() ? NO_CHANGES : CHANGES, null, null)
        members.put('hasChanges', !differences.isEmpty())
        members.put('files', differences.collect { String path, String status ->
            Map<String, Object> each = new LinkedHashMap<>()
            each.put('path', path)
            each.put('status', status)
            each
        })
        return this
    }

    /**
     * @return the document, on one line; JSON writes a control character as an escape, so nothing of
     * it acts on a terminal
     */
    String toJson() {
        return JsonOutput.toJson(members)
    }
}
