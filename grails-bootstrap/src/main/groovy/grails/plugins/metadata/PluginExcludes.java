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
package grails.plugins.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-file-retained annotation stamped by the Grails Groovy compiler onto legacy {@code *GrailsPlugin}
 * descriptors that declare literal {@code pluginExcludes} patterns, allowing build-time bytecode scanners
 * to filter excluded artefacts without loading the plugin class.
 *
 * @since 9.0
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PluginExcludes {

    /**
     * The Ant-style exclude patterns declared by the plugin.
     *
     * @return excluded artefact patterns
     */
    String[] value() default {};
}
