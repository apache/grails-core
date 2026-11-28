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
package org.grails.forge.io

import groovy.transform.CompileStatic
import org.grails.forge.template.Template

@CompileStatic
interface OutputHandler extends Closeable {

    boolean exists(String path)

    void write(String path, Template contents) throws IOException

    String getOutputLocation()

    /**
     * Told once an application is generated, when files of it were not written because there were
     * files of their names already. The state of the project has those files as they would have been
     * generated.
     *
     * @param files the files that were not written, by the path each would have been written to
     * @since 9.0
     */
    default void kept(Map<String, Template> files) {
    }

}
