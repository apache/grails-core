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
package org.apache.grails.datamapping.aot;

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
 * Registers the persistence runtime a datastore reaches through Groovy.
 *
 * <p>Reading and writing an entity goes through the mapping context, the persister for its
 * datastore, and the query and event machinery around them, and Groovy reaches all of it
 * dynamically. Fields are registered as well as methods because a persister hands work to anonymous
 * inner classes that read the state they captured as properties -- deleting an entity reads the
 * session that way -- and a native image that keeps only the members something asked for leaves the
 * class present with that state unreachable.</p>
 *
 * <p>Which datastore is registered follows from what is on the classpath: the scan covers the shared
 * packages, and an implementation puts its own persisters and query support in them, so MongoDB and
 * the others are covered without naming any of them here.</p>
 *
 * <p>Recording this with the framework rather than leaving it to a tracing agent matters because the
 * agent records only what ran. Reads are exercised by anything that opens a page; the write and
 * delete paths reach persister internals that a read never touches, so they fail for the first
 * person who saves or removes a record.</p>
 *
 * @since 8.0
 */
public class GormRuntimeHints implements RuntimeHintsRegistrar {

    private static final Log logger = LogFactory.getLog(GormRuntimeHints.class);

    /**
     * The persistence runtime. Registered by package: naming the types one at a time describes only
     * the operations that have been run so far, and every datastore adds its own.
     */
    private static final String[] PATTERNS = {
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/datastore/mapping/**/*.class",
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/datastore/gorm/**/*.class"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        int registered = 0;
        for (String pattern : PATTERNS) {
            Resource[] resources;
            try {
                resources = resolver.getResources(pattern);
            }
            catch (IOException ex) {
                logger.warn("Unable to scan for the persistence runtime matching " + pattern, ex);
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
                hints.reflection().registerTypeIfPresent(loader, className,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS);
                registered++;
            }
        }
        logger.debug("Registered " + registered + " persistence runtime types for reflection");
    }

}
