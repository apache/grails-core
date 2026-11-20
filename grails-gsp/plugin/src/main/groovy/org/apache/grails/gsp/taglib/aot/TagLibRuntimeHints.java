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
package org.apache.grails.gsp.taglib.aot;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import org.apache.grails.common.aot.RegistrableTypes;

/**
 * Registers the tag libraries and page runtime a rendered page dispatches through.
 *
 * <p>A tag is resolved by name and invoked reflectively, so an image that keeps only the members
 * something asked for renders a page until it reaches a tag it stripped. Which tags those are
 * depends on what the page does: a field is only rendered by a form, and a flash message only
 * exists after a redirect that set one, so a walk of an application's pages exercises neither and
 * they fail for the first person who edits something.</p>
 *
 * <p>Tag libraries are found rather than named. A plugin declares its own in whatever package it
 * chooses, and its tags are as reachable from a page as the framework's own.</p>
 *
 * @since 8.0
 */
public class TagLibRuntimeHints implements RuntimeHintsRegistrar {

    private static final Log logger = LogFactory.getLog(TagLibRuntimeHints.class);

    /**
     * A tag library is identified by its name, wherever it is declared. The trailing wildcard also
     * takes the classes it declares inside itself: the fields plugin keeps the stack a nested tag
     * reads its bean from in one, and a tag that never nests does not reach it.
     */
    private static final String TAGLIB_PATTERN =
            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "**/*TagLib*.class";

    /**
     * The page rendering runtime. Registered by package rather than by name: a compiled page reaches
     * all of it through Groovy, down to writing its output with an operator, and naming the types
     * one at a time only ever describes the pages that have been rendered so far.
     */
    private static final String[] RUNTIME_PATTERNS = {
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/gsp/**/*.class",
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/taglib/**/*.class",
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/buffer/**/*.class"
    };

    private static String[] patterns() {
        String[] all = new String[RUNTIME_PATTERNS.length + 1];
        System.arraycopy(RUNTIME_PATTERNS, 0, all, 0, RUNTIME_PATTERNS.length);
        all[RUNTIME_PATTERNS.length] = TAGLIB_PATTERN;
        return all;
    }

    /**
     * Types the plugin descriptors call. A descriptor is Groovy, so even a static call on a utility
     * class is dispatched dynamically and needs the class to survive.
     */
    private static final String[] CALLED_FROM_DESCRIPTORS = {
        "org.springframework.aot.AotDetector"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        for (String type : CALLED_FROM_DESCRIPTORS) {
            hints.reflection().registerTypeIfPresent(loader, type,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        int registered = 0;
        for (String pattern : patterns()) {
            Resource[] resources;
            try {
                resources = resolver.getResources(pattern);
            }
            catch (IOException ex) {
                logger.warn("Unable to scan for " + pattern, ex);
                continue;
            }
            for (Resource resource : resources) {
                String className;
                try {
                    className = metadataReaderFactory.getMetadataReader(resource)
                            .getClassMetadata().getClassName();
                }
                catch (IOException | RuntimeException ex) {
                    continue;
                }
                if (!RegistrableTypes.loads(className, loader)) {
                    continue;
                }
                // declared rather than public throughout: a tag's body may call a private helper on
                // its own library, and a page reads the shared empty body as a field
                hints.reflection().registerTypeIfPresent(loader, className,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS);
                registered++;
            }
        }
        logger.debug("Registered " + registered + " tag library and page runtime types");
    }
}
