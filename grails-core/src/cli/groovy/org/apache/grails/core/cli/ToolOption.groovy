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
 * Declares an option of a tool: on a field, which then holds the value the tool was given, or on the
 * class, for an option the tool reads itself from {@link ExecutionContext#getCommandLine()}. The
 * declarations are what {@code help}, completion and programs that run tools know of the tool, so a
 * tool that takes options declares every one of them.
 *
 * <pre>
 * &#64;ToolOption(names = ['--since', '-s'], label = 'DATE', description = 'The first day of the report')
 * String since
 * </pre>
 *
 * <p>On a field the type of the field says what the option takes: a {@code boolean} is a switch, a
 * {@code String}, a whole number or an enum takes one value, and a {@code List<String>} can be given
 * more than once. On the class, {@link #arity()} says it, and the annotation can be given more than
 * once there; a set of options that several tools share is a Groovy {@code @AnnotationCollector} of
 * these annotations.</p>
 *
 * <p>The members have to be constants: the compiler reads them from the source, without running the
 * tool.</p>
 *
 * <p>Before the tool runs, the field of an option is set to what the option was given: {@code true} for
 * a switch, the value, its number or its enum constant, or the values of an option given more than
 * once. An option that is not given leaves its field as it starts, unless {@link #defaultValue()} is
 * given, which the field is then set to. A tool that declares an option or a parameter is held to its
 * declarations: arguments that are not what it declares are refused, with exit code 2, before it runs.</p>
 *
 * <p>The rules, which the compiler holds a declaration to with an error that names the field:</p>
 * <ul>
 *   <li>at least one long name, two hyphens and a lower-case letter followed by letters, digits and
 *       single hyphens, at most 32 characters, as {@code --since} or {@code --dataSource}; at most one
 *       short name, a hyphen and one letter; at most four names; none of {@code --help},
 *       {@code --version} and {@code -D}; no name another option of the tool has;</li>
 *   <li>a description of one line of at most 512 characters, and a label of an upper-case letter
 *       followed by at most 31 upper-case letters, digits and hyphens;</li>
 *   <li>a switch is neither required nor has it a label or a default; a default is one line of at most
 *       128 characters, a whole number for a whole number and one of the choices for an enum, and an
 *       option that is required or can be given more than once has none;</li>
 *   <li>an enum has 1 to 64 constants, each of at most 64 ASCII letters, digits, {@code _} and {@code $};</li>
 *   <li>the field is neither static nor final, and is not a parameter as well;</li>
 *   <li>a tool declares at most 32 options.</li>
 * </ul>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.TYPE])
@Repeatable(ToolOptions)
@interface ToolOption {

    /**
     * @return the names of the option: at least one long name, as {@code --since}, and optionally a
     * short one, as {@code -s}
     */
    String[] names()

    /**
     * @return what the option does, one line
     */
    String description() default ''

    /**
     * @return whether the tool is refused without the option
     */
    boolean required() default false

    /**
     * @return the label of the value the option takes, as {@code DATE}; the empty text for none
     */
    String label() default ''

    /**
     * @return the value the option has when it is not given, as the tool reads it; the empty text for none
     */
    String defaultValue() default ''

    /**
     * @return on the class, {@code '0'} for a switch or {@code '1'} for an option that takes a value;
     * on a field the type of the field says it, and this is left out
     */
    String arity() default ''

}
