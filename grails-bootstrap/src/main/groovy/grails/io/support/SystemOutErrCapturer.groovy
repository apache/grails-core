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
package grails.io.support

import groovy.transform.CompileStatic

/**
 * Utility for capturing the result of calls to System.out / System.err
 */
@CompileStatic
class SystemOutErrCapturer {
    ByteArrayOutputStream out
    ByteArrayOutputStream err
    SystemStreamsRedirector previousState
    
    SystemOutErrCapturer capture() {
        out = new ByteArrayOutputStream()
        err = new ByteArrayOutputStream()
        previousState = SystemStreamsRedirector.create(null, new PrintStream(out, true), new PrintStream(err, true)).redirect()
        this
    }
    
    SystemOutErrCapturer redirectToNull() {
        out = null
        err = null
        OutputStream nullStream = new NullOutputStream()
        previousState = SystemStreamsRedirector.create(null, new PrintStream(nullStream, true), new PrintStream(nullStream, true)).redirect()
        this
    }
    
    void close() {
        if(previousState != null) {
            previousState.redirect()
            previousState = null
        }
    }
    
    static <T> T withCapturedOutput(Closure<T> closure) {
        SystemOutErrCapturer capturer = new SystemOutErrCapturer().capture()
        try {
            return closure.call(capturer)
        } finally {
            capturer.close()
        }
    }
    
    static <T> T withNullOutput(Closure<T> closure) {
        SystemOutErrCapturer capturer = new SystemOutErrCapturer().redirectToNull()
        try {
            return closure.call(capturer)
        } finally {
            capturer.close()
        }
    }
    
    @CompileStatic
    static class NullOutputStream extends OutputStream {
        @Override
        void write(byte[] b) throws IOException {
            
        }       
        
        @Override
        void write(int b) throws IOException {
            
        }
        
        @Override
        void write(byte[] b, int off, int len) throws IOException {
            
        }
    }
}
