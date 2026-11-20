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
package org.grails.forge.cli

import java.lang.reflect.Modifier
import groovy.transform.CompileStatic
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import picocli.CommandLine

/**
 * Builds picocli commands from the beans of the Forge application: the commands, what tells the
 * version, and the lists of features an option can have. Everything else picocli asks for, the
 * collections and maps of options and the converters among it, comes from picocli's own factory.
 */
@CompileStatic
class GrailsPicocliFactory implements CommandLine.IFactory {

    private final CommandLine.IFactory defaultFactory = CommandLine.defaultFactory()
    private final ApplicationContext beanContext

    /**
     * Starts the Forge application for the factory, for the picocli code generators run at build
     * time (the completion script and the man pages) that instantiate the commands with a factory
     * named on their command line; the application lives as long as that JVM.
     */
    GrailsPicocliFactory() {
        this(Application.builder().run())
    }

    GrailsPicocliFactory(ApplicationContext beanContext) {
        this.beanContext = beanContext
    }

    @Override
    <K> K create(Class<K> cls) throws Exception {
        if (!isOfTheApplication(cls)) {
            return defaultFactory.create(cls)
        }
        try {
            return beanContext.getBean(cls)
        }
        catch (NoSuchBeanDefinitionException ignored) {
            return defaultFactory.create(cls)
        }
    }

    /**
     * Whether the application is asked for an instance of a type. picocli asks its factory for more
     * than commands: for the collection or the map of an option too, by the type the field has. The
     * application has beans of such types, its configuration as a {@code Map} for one, and what is
     * given on a command line must not end up in them. So the application is asked for what it can
     * only mean to provide: a class of its own, never an interface, an abstract class or a type of
     * the JDK, that is a command, tells the version, or lists the values an option can have.
     */
    private static boolean isOfTheApplication(Class<?> cls) {
        if (cls.isInterface() || cls.isArray() || cls.isPrimitive() || Modifier.isAbstract(cls.getModifiers()) || cls.getClassLoader() == null) {
            return false
        }
        return cls.isAnnotationPresent(CommandLine.Command) ||
                CommandLine.IVersionProvider.isAssignableFrom(cls) ||
                (Iterable.isAssignableFrom(cls) && !Map.isAssignableFrom(cls))
    }

}
