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

@CompileStatic
interface ConsoleOutput {

    ConsoleOutput NOOP = new ConsoleOutput() {
        @Override
        void out(String message) { }

        @Override
        void err(String message) { }

        @Override
        void warning(String message) { }

        @Override
        boolean showStacktrace() {
            return false
        }

        @Override
        boolean verbose() {
            return false
        }

    }

    void out(String message)

    void err(String message)

    void warning(String message)

    boolean showStacktrace()

    boolean verbose()

    default void green(String message) {
        out(message)
    }

    default void red(String message) {
        out(message)
    }
}
