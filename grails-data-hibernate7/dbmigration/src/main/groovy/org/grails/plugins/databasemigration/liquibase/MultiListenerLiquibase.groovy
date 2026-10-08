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
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic

import liquibase.Liquibase
import liquibase.changelog.visitor.ChangeExecListener
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.ChangeExecListenerCommandStep
import liquibase.database.Database
import liquibase.integration.commandline.ChangeExecListenerUtils
import liquibase.resource.ResourceAccessor

/**
 * A {@link Liquibase} that keeps every change listener it is given rather than only the last, so the listener
 * that reports each change set to a startup task, a listener a migration callback sets of its own, and the
 * listener class configured with {@code liquibase.command.changeExecListenerClass} all hear of every change set.
 *
 * <p>Liquibase creates the configured listener only when no listener is set, so it is created here as Liquibase
 * would create it, and the listeners are combined in the {@link #getDefaultChangeExecListener() default listener},
 * which is the one set.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class MultiListenerLiquibase extends Liquibase {

    MultiListenerLiquibase(String changeLogFile, ResourceAccessor resourceAccessor, Database database) {
        super(changeLogFile, resourceAccessor, database)
        defaultChangeExecListener.addListener(configuredChangeExecListener(database, resourceAccessor))
        super.setChangeExecListener(defaultChangeExecListener)
    }

    /** Adds the listener to those already set, rather than taking their place. */
    @Override
    void setChangeExecListener(ChangeExecListener listener) {
        defaultChangeExecListener.addListener(listener)
    }

    /**
     * The listener configured with {@code liquibase.command.changeExecListenerClass}, created as Liquibase's
     * update command creates it, or {@code null} when none is configured.
     */
    private static ChangeExecListener configuredChangeExecListener(Database database, ResourceAccessor resourceAccessor) {
        CommandScope update = new CommandScope(UpdateCommandStep.COMMAND_NAME)
        ChangeExecListenerUtils.getChangeExecListener(database, resourceAccessor,
                update.getArgumentValue(ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_CLASS_ARG),
                update.getArgumentValue(ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_PROPERTIES_FILE_ARG))
    }
}
