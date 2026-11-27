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
 * Declares a parameter of a tool, a value it takes by its place after the options: on a field, which
 * then holds the value the tool was given, or on the class, for a parameter the tool reads itself from
 * {@link ExecutionContext#getCommandLine()}. The declarations are what {@code help}, completion and
 * programs that run tools know of the tool, so a tool that takes parameters declares every one of them.
 *
 * <pre>
 * &#64;ToolParameter(index = 0, label = 'OUTPUT', description = 'The file to write')
 * String output
 * </pre>
 *
 * <p>On a field the type of the field says what the parameter takes: a {@code String}, a whole number
 * or an enum takes one value, and a {@code List<String>}, which only the last parameter can be, takes
 * the rest. On the class, {@link #arity()} says it, {@link #type()} whether a value is a whole number,
 * and the annotation can be given more than once there. {@link #choices()} are the values a parameter
 * on the class, or on a field of the type {@code String}, takes, and {@link #caseInsensitive()} takes
 * them in any case.</p>
 *
 * <p>The members have to be constants: the compiler reads them from the source, without running the
 * tool.</p>
 *
 * <p>Before the tool runs, the field of a parameter is set to the value it was given, its number or its
 * enum constant, or, for the last parameter of the type {@code List<String>}, the values after the
 * parameters before it. A parameter that is not given leaves its field as it starts. The rules, which
 * the compiler holds a declaration to with an error that names the field: a label that is an upper-case
 * letter followed by at most 31 upper-case letters, digits and hyphens, which no other parameter of the
 * tool has; a description of one line of at most 512 characters; indexes from 0 without gaps; only the
 * last parameter takes any number of values; a parameter that has to be given comes before those that
 * can be left out; on the class, an arity of {@code '1'} or {@code '1..*'}, and {@code '0..1'} or
 * {@code '0..*'} only with {@code required = false}; choices as those of {@link ToolOption}, given only
 * on the class or on a field of the type {@code String}, of a parameter of the type {@code string}; on a
 * field no {@code type}; at most 16 parameters.</p>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.TYPE])
@Repeatable(ToolParameters)
@interface ToolParameter {

    /**
     * @return the place of the parameter among the parameters of the tool, from 0 and without gaps
     */
    int index()

    /**
     * @return the label of the parameter, as {@code OUTPUT}
     */
    String label()

    /**
     * @return what the parameter is, one line
     */
    String description() default ''

    /**
     * @return whether the tool is refused without the parameter
     */
    boolean required() default true

    /**
     * @return on the class, how many values the parameter takes, as {@code '1'} or {@code '1..*'}; on a
     * field the type of the field says it, and this is left out
     */
    String arity() default ''

    /**
     * @return on the class, what each value of the parameter is: {@code 'string'}, which a parameter without
     * this is, or {@code 'integer'}, a whole number, which the tool still reads as the text it was given
     * ({@code ToolDescriptor.TYPE_STRING} and {@code ToolDescriptor.TYPE_INTEGER} of
     * {@code grails-gradle-model}). On a field the type of the field says it, and this is left out.
     * @since 9.0
     */
    String type() default ''

    /**
     * @return the values the parameter takes, and no other, as {@code ['generate', 'export']}: on the class,
     * or on a field of the type {@code String}; on a field of any other type its type says what it takes,
     * and an enum has its constants for choices. None when empty.
     * @since 9.0
     */
    String[] choices() default []

    /**
     * @return whether a value is one of the {@link #choices()} whatever its case; the tool is given the
     * value as it was given. Only with choices
     * @since 9.0
     */
    boolean caseInsensitive() default false

}
