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

package grails.views

import groovy.transform.CompileStatic

/**
 * Interface for view configurations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface ViewConfiguration {

    /**
     * @return Should compile statically
     */
    boolean isCompileStatic()

    /**
     * @return Whether to allow resource expansion
     */
    boolean isAllowResourceExpansion()

    /**
     * @return Whether reloading is enabled
     */
    boolean isEnableReloading()

    /**
     * @return Whether to pretty print
     */
    boolean isPrettyPrint()

    /**
     * @return Whether to use absolute links
     */
    boolean isUseAbsoluteLinks()

    /**
     * @return The package name
     */
    String getPackageName()

    /**
     * @return The file extension
     */
    String getExtension()

    /**
     * @return The template base class
     */
    Class getBaseTemplateClass()

    /**
     * @return Whether to cache
     */
    boolean isCache()

    /**
     * @return Path to the templates
     */
    String getTemplatePath()

    /**
     * @return The packages to automatically import
     */
    String[] getPackageImports()

    /**
     * @return The static imports to automatically import
     */
    String[] getStaticImports()

    /**
     * @return The name of the views module (example json or markup)
     */
    String getViewModuleName()

    /**
     * @return The default encoding to use to render views
     */
    String getEncoding()
}