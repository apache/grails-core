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
package org.grails.datastore.mapping.config

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import groovy.util.logging.Slf4j

import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.reflect.NameUtils

/**
 * A generic configuration builder that implementers can implement to construct the configuration from the source {@link PropertyResolver}
 *
 * @param <B> The builder type (examples are MongoClientSettings.Builder or Neo4j Bolt's Config.ConfigBuilder
 * @param <C> The finalized configuration constructions from the builder (examples are MongoClientSettings or Neo4j Bolt's Config)
 *
 * @author Graeme Rocher
 */
@CompileStatic
@Slf4j
abstract class ConfigurationBuilder<B, C> {
    private static final Set<String> IGNORE_METHODS = ['seProperty', 'propertyMissing'] as Set
    final PropertyResolver propertyResolver
    final String configurationPrefix
    final String builderMethodPrefix
    final Object fallBackConfiguration
    protected B rootBuilder

    /**
     * @param propertyResolver The property resolver
     * @param configurationPrefix The prefix to resolve settings from within the configuration. Example "grails.gorm.neo4j" or "grails.gorm.mongodb"
     * @param builderMethodPrefix The prefix to builder method calls. Default is null which results in builder methods like "foo(...)". Seting a prefix of "with" results in "withFoo(..)"
     *
     */
    @CompileDynamic
    ConfigurationBuilder(PropertyResolver propertyResolver, String configurationPrefix, String builderMethodPrefix) {
        this.propertyResolver = propertyResolver
        this.configurationPrefix = configurationPrefix
        this.builderMethodPrefix = builderMethodPrefix
        this.fallBackConfiguration = null
    }

    /**
     * @param propertyResolver The property resolver
     * @param configurationPrefix The prefix to resolve settings from within the configuration. Example "grails.gorm.neo4j" or "grails.gorm.mongodb"
     * @param builderMethodPrefix The prefix to builder method calls. Default is null which results in builder methods like "foo(...)". Seting a prefix of "with" results in "withFoo(..)"
     * @param fallBackConfiguration An object to read the fallback configuration from
     */
    @CompileDynamic
    ConfigurationBuilder(PropertyResolver propertyResolver, String configurationPrefix, Object fallBackConfiguration = null, String builderMethodPrefix = null) {
        this.propertyResolver = propertyResolver
        this.configurationPrefix = configurationPrefix
        this.builderMethodPrefix = builderMethodPrefix
        if(fallBackConfiguration != null) {
            def cloned
            try {
                cloned = fallBackConfiguration.clone()
            } catch (CloneNotSupportedException e) {
                cloned = fallBackConfiguration
            }
            this.fallBackConfiguration = cloned
        }
        else {
            this.fallBackConfiguration = null
        }
    }



    C build() {
        rootBuilder = createBuilder()
        buildInternal(rootBuilder, this.configurationPrefix)
    }

    /**
     * Creates the native builder
     *
     * @return The native builder
     */
    protected abstract B createBuilder()

    /**
     * Convert the builder to the final configuration
     *
     * @param builder The builder
     * @return The final configuration
     */
    protected abstract C toConfiguration(B builder)

    private C buildInternal(B builder, String startingPrefix) {
        buildRecurse(builder, new ArrayList<Class>(), this.fallBackConfiguration, startingPrefix)

        return toConfiguration(builder)
    }

    private List<Class> toHierarchy(Class cls) {
        List<Class> classes = [cls]
        while(cls != Object) {
            def superClass = cls.getSuperclass()
            if(superClass == Object || superClass == LinkedHashMap) break

            classes.add(superClass)
            cls = superClass
        }
        return classes.reverse()
    }

    /**
     * @deprecated use {@link ConfigurationBuilder#buildRecurse(Object, List, Object, String)} instead
     */
    protected void buildRecurse(Object builder, Object fallBackConfig, String startingPrefix) {
       buildRecurse(builder, new ArrayList<Class>(), fallBackConfig, startingPrefix)
    }

    protected void buildRecurse(Object builder, List<Class> builderQueue, Object fallBackConfig, String startingPrefix) {

        List<Class> hierarchy = toHierarchy(builder.getClass())

        startBuild(builder, startingPrefix)

        for(Class builderClass in hierarchy) {

            def methods = builderClass.declaredMethods
            for (method in methods) {
                def methodName = method.name
                if (!Modifier.isPublic(method.modifiers) || method.isSynthetic() || IGNORE_METHODS.contains(methodName)) {
                    continue
                }
                if (method.declaringClass != builderClass) {
                    continue
                }
                def parameterTypes = method.parameterTypes

                String settingName

                boolean hasBuilderPrefix = builderMethodPrefix != null

                if (hasBuilderPrefix && methodName.startsWith(builderMethodPrefix)) {
                    settingName = methodName.substring(builderMethodPrefix.size()).uncapitalize()
                }
                else if (hasBuilderPrefix) {
                    continue
                }
                else if (!hasBuilderPrefix &&
                        ((org.grails.datastore.mapping.reflect.ReflectionUtils.isGetter(methodName, parameterTypes) && method.returnType.getAnnotation(Builder) == null) ||
                                org.grails.datastore.mapping.reflect.ReflectionUtils.isSetter(methodName, parameterTypes))) {
                    // don't process getters or setters, unless the getter returns a builder
                    continue
                }
                else {
                    settingName = methodName
                }

                String propertyPath = startingPrefix ? "${startingPrefix}.${settingName}" : settingName

                if (parameterTypes.length == 1) {
                    Class argType = parameterTypes[0]

                    def builderMethod = ReflectionUtils.findMethod(argType, 'builder')
                    if (builderMethod != null && Modifier.isStatic(builderMethod.modifiers)) {
                        if (propertyResolver.containsProperty(propertyPath)) {
                            Method existingGetter = ReflectionUtils.findMethod(builderClass, NameUtils.getGetterName(methodName))
                            def newBuilder

                            if (existingGetter != null) {
                                newBuilder = existingGetter.invoke(builder)
                            }
                            if (newBuilder == null) {
                                newBuilder = builderMethod.invoke(argType)
                            }

                            newChildBuilder(newBuilder, propertyPath)

                            Object fallBackChildConfig = getFallBackValue(fallBackConfig, settingName)
                            if (!builderQueue.contains(newBuilder.class)) {
                                builderQueue.add(newBuilder.class)
                                buildRecurse(newBuilder, builderQueue, fallBackChildConfig, propertyPath)
                                builderQueue.remove(newBuilder.class)

                                def buildMethod = ReflectionUtils.findMethod(newBuilder.getClass(), 'build')
                                if (buildMethod != null) {
                                    try {
                                        method.invoke(builder, buildMethod.invoke(newBuilder))
                                    } catch (Throwable e) {
                                        log.error("build method threw exception", e)
                                    }
                                } else {
                                    method.invoke(builder, newBuilder)
                                }
                            }
                        }

                        continue
                    }

                    def buildMethod = ReflectionUtils.findMethod(argType, 'build')
                    if (buildMethod != null) {
                        Method existingGetter = ReflectionUtils.findMethod(builderClass, NameUtils.getGetterName(methodName))
                        def newBuilder

                        if (existingGetter != null) {
                            newBuilder = existingGetter.invoke(builder)

                            if (newBuilder != null) {
                                Object fallBackChildConfig = getFallBackValue(fallBackConfig, settingName)
                                newBuilder = newChildBuilderForFallback(newBuilder, fallBackChildConfig)
                                if (!builderQueue.contains(newBuilder.class)) {
                                    builderQueue.add(newBuilder.class)
                                    buildRecurse(newBuilder, builderQueue, fallBackChildConfig, propertyPath)
                                    builderQueue.remove(newBuilder.class)
                                    newChildBuilder(newBuilder, propertyPath)
                                    method.invoke(builder, newBuilder)
                                }
                                continue
                            }
                        }
                    }

                    Builder builderAnnotation = argType.getAnnotation(Builder)
                    if (builderAnnotation != null && builderAnnotation.builderStrategy() == SimpleStrategy) {
                        Method existingGetter = ReflectionUtils.findMethod(builderClass, NameUtils.getGetterName(methodName))
                        def newBuilder
                        if (existingGetter != null) {
                            newBuilder = existingGetter.invoke(builder)
                        }
                        if (newBuilder == null) {
                            newBuilder = argType.newInstance()
                        }

                        if (newBuilder instanceof Map) {
                            Map subMap = propertyResolver.getProperty(propertyPath, Map, Collections.emptyMap())
                            if (!subMap.isEmpty()) {
                                ((Map) newBuilder).putAll(subMap)
                            }
                        }

                        newChildBuilder(newBuilder, propertyPath)

                        Object fallBackChildConfig = getFallBackValue(fallBackConfig, methodName)
                        if (!builderQueue.contains(newBuilder.class)) {
                            builderQueue.add(newBuilder.class)
                            buildRecurse(newBuilder, builderQueue, fallBackChildConfig, propertyPath)
                            builderQueue.remove(newBuilder.class)
                            method.invoke(builder, newBuilder)
                        }
                        continue
                    }

                    if (ConfigurationBuilder.isAssignableFrom(argType)) {
                        try {
                            Method existingGetter = ReflectionUtils.findMethod(builderClass, NameUtils.getGetterName(methodName))
                            ConfigurationBuilder newBuilder
                            if (existingGetter != null) {
                                newBuilder = (ConfigurationBuilder) existingGetter.invoke(builder)
                            }
                            if (newBuilder == null) {

                                if (fallBackConfig != null && builderClass.isInstance(fallBackConfig)) {

                                    ConfigurationBuilder fallbackBuilder = (ConfigurationBuilder) existingGetter.invoke(fallBackConfig)
                                    if (fallbackBuilder != null) {
                                        newBuilder = (ConfigurationBuilder) argType.newInstance(this.propertyResolver, propertyPath, fallbackBuilder.build())
                                    } else {
                                        newBuilder = (ConfigurationBuilder) argType.newInstance(this.propertyResolver, propertyPath)
                                    }
                                } else {
                                    newBuilder = (ConfigurationBuilder) argType.newInstance(this.propertyResolver, propertyPath)
                                }


                            }
                            newChildBuilder(newBuilder, propertyPath)
                            method.invoke(builder, newBuilder)
                        } catch (Throwable e) {
                            throw new ConfigurationException("Cannot read configuration for path $propertyPath: $e.message", e)
                        }
                        continue
                    }
                } else if (methodName.startsWith("get") && parameterTypes.length == 0) {
                    if (method.returnType.getAnnotation(Builder)) {
                        def childBuilder = method.invoke(builder)
                        if (childBuilder != null) {
                            Object fallBackChildConfig = null
                            if (fallBackConfig != null) {
                                Method fallbackGetter = ReflectionUtils.findMethod(fallBackConfig.getClass(), methodName)
                                if (fallbackGetter != null) {
                                    fallBackChildConfig = fallbackGetter.invoke(fallBackConfig)
                                }
                            }

                            String getterPropertyPath = startingPrefix ? "${startingPrefix}.${NameUtils.getPropertyNameForGetterOrSetter(methodName)}" : NameUtils.getPropertyNameForGetterOrSetter(methodName)
                            if (!builderQueue.contains(childBuilder.class)) {
                                builderQueue.add(childBuilder.class)
                                buildRecurse(childBuilder, builderQueue, fallBackChildConfig, getterPropertyPath)
                                builderQueue.remove(childBuilder.class)
                            }
                            continue
                        }
                    }
                } else if (parameterTypes.length == 0) {
                    def value = propertyResolver.getProperty(propertyPath, Boolean, false)
                    if (value) {
                        try {
                            method.invoke(builder)
                        } catch (Throwable e) {
                            throw new ConfigurationException("Error executing method for path $propertyPath: $e.message", e)
                        }
                    }
                    continue
                }

                List<Object> args = []

                boolean appendArgName = parameterTypes.length > 1
                int argIndex = 0

                for (Class argType: parameterTypes) {
                    String propertyPathForArg = propertyPath
                    if (appendArgName) {
                        propertyPathForArg += ".arg${argIndex}"
                    }
                    argIndex++
                    def valueOfMethod = ReflectionUtils.findMethod(argType, 'valueOf')
                    if (valueOfMethod != null && Modifier.isStatic(valueOfMethod.modifiers)) {
                        try {
                            def value = propertyResolver.getProperty(propertyPathForArg, "")
                            if (value) {
                                def converted = valueOfMethod.invoke(argType, value)
                                args.add(converted)
                            }
                        } catch (Throwable e) {
                            throw new ConfigurationException("Cannot read configuration for path $propertyPathForArg: $e.message", e)
                        }
                    }
                    else {
                        Object fallBackValue = getFallBackValue(fallBackConfig, settingName)

                        def value
                        try {
                            value = propertyResolver.getProperty(propertyPathForArg, argType, fallBackValue)
                        } catch (ConversionFailedException e) {
                            if(argType.isEnum()) {
                                value = propertyResolver.getProperty(propertyPathForArg, String)
                                if (value != null) {
                                    try {
                                        value = Enum.valueOf((Class)argType, value.toUpperCase())
                                    } catch (Throwable e2) {
                                        // ignore e2 and throw original
                                        throw new ConfigurationException("Invalid value for setting [$propertyPathForArg]: $e.message", e)
                                    }
                                }
                                else {
                                    throw new ConfigurationException("Invalid value for setting [$propertyPathForArg]: $e.message", e)
                                }
                            }
                            else {
                                throw new ConfigurationException("Invalid value for setting [$propertyPathForArg]: $e.message", e)
                            }
                        }
                        if (value != null) {
                            log.debug("Resolved value [{}] for setting [{}]", value, propertyPathForArg)
                            args.add(value)
                        }

                    }
                }

                if (args) {
                    ReflectionUtils.makeAccessible(method)
                    ReflectionUtils.invokeMethod(method, builder, args.toArray())
                }
            }

        }

    }

    protected Object newChildBuilderForFallback(Object childBuilder, Object fallbackConfig) {
        return childBuilder
    }

    protected Object getFallBackValue(fallBackConfig, String methodName) {
        Object fallBackValue = null
        if (fallBackConfig != null) {
            Method fallbackGetter = ReflectionUtils.findMethod(fallBackConfig.getClass(), NameUtils.getGetterName(methodName))
            if (fallbackGetter != null && Modifier.isPublic(fallbackGetter.getModifiers())) {
                fallBackValue = fallbackGetter.invoke(fallBackConfig)
            }
        }
        return fallBackValue
    }

    /**
     * Subclasses can override for when a new child builder is created
     *
     * @param builder The builder
     * @param configurationPath The configuration path
     */
    protected void newChildBuilder(Object builder, String configurationPath) {
        // no-op
    }

    /**
     * Subclasses can override for when building starts for the given builder
     *
     * @param builder The current builder
     * @param configurationPath The configuration path
     */
    protected void startBuild(Object builder, String configurationPath) {
        // no-op
    }
}
