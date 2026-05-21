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

package org.grails.scaffolding


class ClosureCapture {
    boolean stopOnException = true
    private boolean hasException = false
    private RootCall root = new RootCall(calls: [])
    private RootCall current = root
    List<Call> getCalls() { return root.calls }
    def invokeMethod(String name, args) {
        Call call = new Call(name: name, args: args, parent: current, calls: [], throwable: null)
        current.calls << call
        if(args && args[-1] instanceof Closure) {
            RootCall previousCall = current
            current = call
            Closure c = args[-1]
            def previousDelegate = c.delegate
            c.delegate = this
            try {
                c.call()
            } catch(Throwable t) {
                call.throwable = t
                hasException = true
                if(stopOnException) {
                    throw t
                }
            } finally {
                c.delegate = previousDelegate
            }
            current = previousCall
        }
    }

    private static class RootCall {
        @Delegate
        List<Call> calls
    }

    private static class Call extends RootCall {
        String name
        def args
        RootCall parent
        Throwable throwable
    }
}