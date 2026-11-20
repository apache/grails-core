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

import java.lang.reflect.Constructor
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
    private final ForgeContext beanContext

    /**
     * For the picocli code generators run at build time (the completion script and the man pages),
     * which instantiate the commands with a factory named on their command line: the Forge
     * application is started when they list the values of an option that it has, and lives as long
     * as that JVM.
     */
    GrailsPicocliFactory() {
        this(new ForgeContext())
    }

    GrailsPicocliFactory(ApplicationContext beanContext) {
        this(ForgeContext.of(beanContext))
    }

    /**
     * @param beanContext the application, which the factory does not start: what it makes asks for
     * the application when it needs it
     */
    GrailsPicocliFactory(ForgeContext beanContext) {
        this.beanContext = beanContext
    }

    /**
     * Makes a command, or what a command names in its annotations, without starting the application
     * where that can be done: a class that has a constructor for a {@link ForgeContext} is given it
     * and asks for its beans when it runs; the values of an option that are a bean are listed when
     * they are asked for; a class that needs nothing is made as it is. Only a class that can be made
     * in no other way is asked of the application, which is started for it.
     */
    @Override
    <K> K create(Class<K> cls) throws Exception {
        if (!isOfTheApplication(cls)) {
            return defaultFactory.create(cls)
        }
        // the same whether the application runs already or not, so that a command line is what it is either way
        Constructor<K> lazy = constructor(cls, ForgeContext)
        if (lazy != null) {
            return lazy.newInstance(beanContext)
        }
        if (constructor(cls) != null) {
            return defaultFactory.create(cls)
        }
        if (Iterable.isAssignableFrom(cls)) {
            return (K) candidates(cls)
        }
        try {
            return beanContext.get().getBean(cls)
        }
        catch (NoSuchBeanDefinitionException ignored) {
            return defaultFactory.create(cls)
        }
    }

    // picocli asks for the values of an option as an Iterable and keeps what it is given
    private Object candidates(Class<?> cls) {
        return new ForgeContext.Candidates(beanContext, (Class<? extends Iterable>) cls)
    }

    private static <K> Constructor<K> constructor(Class<K> cls, Class<?>... parameters) {
        try {
            return cls.getConstructor(parameters)
        } catch (NoSuchMethodException ignored) {
            return null
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
