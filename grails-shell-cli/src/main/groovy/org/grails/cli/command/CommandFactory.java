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
package org.grails.cli.command;

import org.springframework.boot.cli.command.Command;

import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Factory used to create CLI {@link org.springframework.boot.cli.command.Command}s. Intended for use with a Java
 * {@link ServiceLoader}.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@FunctionalInterface
public interface CommandFactory {

    /**
     * Returns the CLI {@link org.springframework.boot.cli.command.Command}s.
     * @return the commands
     */
    Collection<Command> getCommands();

}
