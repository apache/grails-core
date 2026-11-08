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
package org.apache.grails.gsp.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import org.grails.gsp.GroovyPageMetaInfo;

/**
 * Registers what a page compiled at build time is read from.
 *
 * <p>Compiling a page splits it: the code becomes a class, and the static text between the code --
 * most of the page -- is written beside it as a resource, along with the line numbers that map the
 * generated code back to the page it came from. The class reads that resource as it renders.</p>
 *
 * <p>An image carries a resource only when it has been asked to, and nothing asked for these: they
 * are named by a convention rather than by any code. The page then rendered with nothing where its
 * text should be, and reported a null the page itself could not explain.</p>
 *
 * @since 8.0
 */
public class PrecompiledPageRuntimeHints implements RuntimeHintsRegistrar {

    /** Where the pages compiled at build time are listed, read to find them at all. */
    private static final String VIEWS = "gsp/views.properties";

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(VIEWS);
        hints.resources().registerPattern("*" + GroovyPageMetaInfo.HTML_DATA_POSTFIX);
        hints.resources().registerPattern("*" + GroovyPageMetaInfo.LINENUMBERS_DATA_POSTFIX);
    }
}
