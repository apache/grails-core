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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * What was done about a change of a project that was interrupted: see {@link FeatureAddition#recover}.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectRecovery {

    /** Whether there was a change that was interrupted and its files were put back: the project is as it was before the change */
    final boolean undone

    /** Whether there was a change that had written every file and only had not ended: it is ended now, and the project has the change */
    final boolean completed

    /** The files that were put back, by their paths in the project, the files of the state under {@code .grails} among them */
    final List<String> putBack

    /** The files the change had not come to and that are not what they were when it began: they were changed since, and are left as they are */
    final List<String> keptAsTheyAre

    /**
     * The names of what is in {@code .grails/transactions} and is of no change this user began. It
     * came with the project, or another user left it; it is never read and is left where it is.
     */
    final List<String> strangers

    @PackageScope
    ProjectRecovery(boolean undone, boolean completed, List<String> putBack, List<String> keptAsTheyAre, List<String> strangers) {
        this.undone = undone
        this.completed = completed
        this.putBack = Collections.unmodifiableList(new ArrayList<String>(putBack))
        this.keptAsTheyAre = Collections.unmodifiableList(new ArrayList<String>(keptAsTheyAre))
        this.strangers = Collections.unmodifiableList(new ArrayList<String>(strangers))
    }

    /**
     * What to tell whoever ran the command, a line each and none when there is nothing to tell. The
     * lines name files by their paths in the project and have no character that acts on a terminal.
     */
    List<String> getMessages() {
        List<String> messages = []
        if (completed) {
            messages << 'An earlier change of the application was interrupted after its last file was written: it is complete, and was ended now'
        }
        if (undone) {
            messages << ("An earlier change of the application that was interrupted was undone. ${putBack.size()} ${putBack.size() == 1 ? 'file was' : 'files were'} " +
                    "put back as ${putBack.size() == 1 ? 'it was' : 'they were'}: ${putBack.join(', ')}").toString()
        }
        if (!keptAsTheyAre.isEmpty()) {
            messages << ("The interrupted change had not come to ${keptAsTheyAre.join(', ')}, which ${keptAsTheyAre.size() == 1 ? 'was' : 'were'} changed since and " +
                    "${keptAsTheyAre.size() == 1 ? 'is' : 'are'} left as ${keptAsTheyAre.size() == 1 ? 'it is' : 'they are'}").toString()
        }
        if (!strangers.isEmpty()) {
            messages << ("${ProjectTransaction.DIRECTORY} has ${strangers.join(', ')}, which no change this command began left there: it was not read and is left alone. " +
                    'It can be removed').toString()
        }
        return messages.collect { String message -> message.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?').replace('@|', '@ |') }
    }
}
