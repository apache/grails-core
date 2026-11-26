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
package org.apache.grails.core.cli

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Repeatable
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Describes one option of an {@link ApplicationCommand}, so that command line tools can show it in the
 * command's help and complete it.
 *
 * <p>An option is given as {@code --name=value}, or as {@code --name} when it is a {@link #flag()}:</p>
 *
 * <pre>
 * &#64;CommandOption(name = 'groupClassName', description = 'Also generate role group classes with this name')
 * &#64;CommandOption(name = 'uiOnly', flag = true, description = 'Configure the plugin without generating classes')
 * class SetupUsersCommand implements GrailsApplicationCommand { ... }
 * </pre>
 *
 * <p>The annotation only describes the option. The command still reads it from the command line, for
 * example through {@code executionContext.commandLine}. A command that declares no option accepts any
 * options.</p>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(CommandOptions)
@interface CommandOption {

    /**
     * @return the name of the option, without the leading {@code --}
     */
    String name()

    /**
     * @return the description of the option, shown in the command's help
     */
    String description() default ''

    /**
     * @return whether the option is a flag, given without a value
     */
    boolean flag() default false

    /**
     * @return the values offered when the option's value is completed; none when it takes any value
     */
    String[] values() default []
}
