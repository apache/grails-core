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
package org.apache.grails.xml.aot;

import java.util.List;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import grails.converters.XML;
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer;
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator;

/**
 * Registers the XML converter that {@code encodeAsXML} creates by name, and the initializer of its configurations.
 *
 * <p>The {@code xml} encoder of {@code grails-converters} renders an object other than text with the XML converter of
 * this module, which depends on {@code grails-converters}, so it finds the converter by its name and creates it with
 * its constructor that takes the object. An ahead-of-time image keeps neither unless something asks for them, and
 * without them {@code encodeAsXML} escapes an object as its string, as it does without this module. The converters'
 * initializer, when code initializes the converters itself, creates the initializer of the XML converter by name the
 * same way, and the data binding registry its default XML binding source creator.</p>
 *
 * @since 9.0
 */
@SuppressWarnings("removal")
public class XmlRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(XML.class,
                type -> type.withConstructor(List.of(TypeReference.of(Object.class)), ExecutableMode.INVOKE));
        hints.reflection().registerType(XmlConvertersConfigurationInitializer.class,
                type -> type.withConstructor(List.of(), ExecutableMode.INVOKE));
        hints.reflection().registerType(XmlDataBindingSourceCreator.class,
                type -> type.withConstructor(List.of(), ExecutableMode.INVOKE));
    }
}
