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
package org.grails.web.databinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The property names Grails computed for data binding, recorded on the class so that they are read from the class
 * file once the class is compiled. A subclass in another module, in an application that uses a plugin, or in an
 * incremental build sees its parent only as bytecode, which has no constraints closure and no {@code transients}
 * list to read. Without this record, the subclass would bind what the parent declared transient or
 * {@code bindable: false}, and would not bind what the parent made bindable by constraint.
 *
 * <p>A record is written with every white list field. Domain classes and the parameter types of controller actions
 * get theirs from the injectors that process them; any other class with binding rules, one that declares
 * constraints or transients, implements {@code grails.validation.Validateable}, or extends a class that does, gets
 * its field and its record after canonicalization, wherever it is compiled, so that a command object compiled
 * apart from the controller that binds it keeps its rules.</p>
 *
 * <p>Written by {@link DefaultASTDatabindingHelper}; not meant to be applied by hand.</p>
 *
 * @since 7.0.18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GeneratedDatabindingWhiteList {

    /**
     * The names a subclass inherits into its white list, without the {@code _*} and {@code .*} patterns the
     * generated field adds for non-simple properties.
     */
    String[] value();

    /**
     * The special properties, {@code id}, {@code version}, {@code dateCreated} and {@code lastUpdated}, that an
     * explicit {@code bindable: true} constraint in the class or its parents makes bindable in a domain class.
     */
    String[] explicitlyBindable() default {};
}
