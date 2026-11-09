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
package org.apache.grails.core.aot;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import org.apache.grails.common.aot.RegistrableTypes;

/**
 * Registers the classes that extend types Groovy did not declare.
 *
 * <p>A module declares them in a descriptor, and Groovy calls the method it finds there through the
 * metaclass rather than as a call written against the class -- so {@code request.forwardURI} reaches
 * a static method on an extension class reflectively. An image keeps that method only when asked,
 * and nothing asks: the extension class is named in a descriptor rather than in any code.</p>
 *
 * <p>The image then refuses the call where it is made. The framework extends the servlet request,
 * the response, the session and more this way, and those are reached while rendering a page, so what
 * fails is a request rather than the start-up -- most visibly the error page, which is the one page
 * that renders when something has already gone wrong.</p>
 *
 * <p>Read from the descriptors while the hints are written, so a module added later is covered
 * without being named here, whether it belongs to the framework, a plugin or the application.</p>
 *
 * <p>The descriptors themselves are carried into the image too, along with the table of methods
 * Groovy adds to every type. Both are read as the runtime starts, and an image carries a resource
 * only when it has been asked to: without them Groovy cannot build its metaclasses at all, and
 * fails before any application code runs.</p>
 *
 * @since 8.0
 */
public class GroovyExtensionModuleRuntimeHints implements RuntimeHintsRegistrar {

    private static final Log logger = LogFactory.getLog(GroovyExtensionModuleRuntimeHints.class);

    private static final String DESCRIPTOR_NAME = "org.codehaus.groovy.runtime.ExtensionModule";

    /** Both places Groovy reads module descriptors from. */
    private static final String[] DESCRIPTORS = {
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "META-INF/services/" + DESCRIPTOR_NAME,
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "META-INF/groovy/" + DESCRIPTOR_NAME
    };

    /** The two kinds of extension a descriptor names: one extends instances, the other the type. */
    private static final String[] CLASS_PROPERTIES = { "extensionClasses", "staticExtensionClasses" };

    /**
     * What the Groovy runtime reads as it starts: the table of the methods it adds to every type,
     * the version it reports, and the descriptors naming the extensions above.
     */
    private static final String[] RESOURCES = {
        "META-INF/dgminfo",
        "META-INF/groovy-release-info.properties",
        "META-INF/services/" + DESCRIPTOR_NAME,
        "META-INF/groovy/" + DESCRIPTOR_NAME
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String resource : RESOURCES) {
            hints.resources().registerPattern(resource);
        }
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        Set<String> extensionClasses = new LinkedHashSet<>();
        for (String descriptor : DESCRIPTORS) {
            collectFrom(resolver, descriptor, extensionClasses);
        }
        int registered = 0;
        for (String className : extensionClasses) {
            if (RegistrableTypes.loads(className, loader)) {
                hints.reflection().registerTypeIfPresent(loader, className,
                        MemberCategory.INVOKE_DECLARED_METHODS);
                registered++;
            }
        }
        logger.debug("Registered " + registered + " Groovy extension classes for reflection");
    }

    private void collectFrom(ResourcePatternResolver resolver, String descriptor, Set<String> collected) {
        Resource[] resources;
        try {
            resources = resolver.getResources(descriptor);
        }
        catch (IOException ex) {
            logger.warn("Unable to read Groovy extension modules from " + descriptor, ex);
            return;
        }
        for (Resource resource : resources) {
            Properties module = read(resource);
            if (module == null) {
                continue;
            }
            for (String property : CLASS_PROPERTIES) {
                for (String className : StringUtils.commaDelimitedListToStringArray(
                        module.getProperty(property, ""))) {
                    String trimmed = className.trim();
                    if (!trimmed.isEmpty()) {
                        collected.add(trimmed);
                    }
                }
            }
        }
    }

    /** A descriptor that cannot be read names nothing, which is what an unreadable one contributes. */
    @Nullable
    private Properties read(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            Properties module = new Properties();
            module.load(input);
            return module;
        }
        catch (IOException | RuntimeException ex) {
            logger.warn("Unable to read Groovy extension module " + resource, ex);
            return null;
        }
    }
}
