/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.cli.profile;

import org.grails.build.parsing.CommandLine;


/**
 * Context for the execution of {@link org.grails.cli.profile.Command} instances within a {@link org.grails.cli.profile.Profile}
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 */
public interface ExecutionContext extends ProjectContext {

    /**
     * @return The parsed command line arguments as an instance of {@link org.grails.build.parsing.CommandLine}
     */
    CommandLine getCommandLine();

    /**
     * Allows cancelling of the running command
     */
    void cancel();

    /**
     * Attaches a listener for cancellation events
     *
     * @param listener The {@link CommandCancellationListener}
     */
    void addCancelledListener(CommandCancellationListener listener);
}
