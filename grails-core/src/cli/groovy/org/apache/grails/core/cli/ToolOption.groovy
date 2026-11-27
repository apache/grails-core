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
