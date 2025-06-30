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
package org.grails.forge.template;

public interface RenderResult {

    boolean isSuccess();

    boolean isSkipped();

    String getPath();

    Exception getError();

    static RenderResult skipped(String path) {
        return new RenderResult() {
            @Override
            public boolean isSuccess() {
                return false;
            }

            @Override
            public boolean isSkipped() {
                return true;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public Exception getError() {
                return null;
            }
        };
    }

    static RenderResult success(String path) {
        return new RenderResult() {
            @Override
            public boolean isSuccess() {
                return true;
            }

            @Override
            public boolean isSkipped() {
                return false;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public Exception getError() {
                return null;
            }
        };
    }

    static RenderResult error(String path, Exception t) {
        return new RenderResult() {
            @Override
            public boolean isSuccess() {
                return false;
            }

            @Override
            public boolean isSkipped() {
                return false;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public Exception getError() {
                return t;
            }
        };
    }
}
