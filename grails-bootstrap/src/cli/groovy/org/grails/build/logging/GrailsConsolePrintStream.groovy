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
package org.grails.build.logging

import groovy.transform.CompileStatic

import grails.build.logging.GrailsConsole

/**
 * Used to replace default System.out with one that routes calls through GrailsConsole.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class GrailsConsolePrintStream extends PrintStream {

    GrailsConsolePrintStream(PrintStream out) {
        super(out, true)
    }

    PrintStream getTargetOut() {
        return (PrintStream) out
    }

    @Override
    void print(Object o) {
        if (o != null) {
            GrailsConsole.getInstance().log(o.toString())
        }
    }

    @Override
    void print(String s) {
        GrailsConsole.getInstance().log(s)
    }

    @Override
    void println(String s) {
        GrailsConsole.getInstance().log(s)
    }

    @Override
    void println(Object o) {
        if (o != null) {
            GrailsConsole.getInstance().log(o.toString())
        }
    }

}
