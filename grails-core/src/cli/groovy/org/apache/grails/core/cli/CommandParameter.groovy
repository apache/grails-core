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
 * Describes one positional argument of an {@link ApplicationCommand}, so that command line tools can
 * show it in the command's help and complete it.
 *
 * <p>Declare one annotation per argument, in the order the arguments are given on the command line:</p>
 *
 * <pre>
 * &#64;CommandParameter(name = 'package', description = 'The package of the domain classes')
 * &#64;CommandParameter(name = 'user-class-name', description = 'The name of the user class')
 * class SetupUsersCommand implements GrailsApplicationCommand { ... }
 * </pre>
 *
 * <p>The annotation only describes the argument. The command still reads its value from the command
 * line, for example through {@code args} of {@link GrailsApplicationCommand}. A command that declares
 * no argument accepts any arguments.</p>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(CommandParameters)
@interface CommandParameter {

    /**
     * @return the name of the argument, shown in the command's help
     */
    String name()

    /**
     * @return the description of the argument, shown in the command's help
     */
    String description() default ''

    /**
     * @return whether the command needs the argument
     */
    boolean required() default true

    /**
     * @return the values offered when the argument is completed; none when the argument takes any value
     */
    String[] values() default []
}
