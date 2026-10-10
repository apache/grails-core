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
package org.grails.taglib.encoder

import groovy.transform.CompileStatic

import org.grails.encoder.Encoder

@CompileStatic
class OutputEncodingStackAttributes {

    private final Writer topWriter
    private final Encoder staticEncoder
    private final Encoder outEncoder
    private final Encoder expressionEncoder
    private final Encoder taglibEncoder
    private final Encoder defaultTaglibEncoder
    private final boolean allowCreate
    private final boolean pushTop
    private final boolean autoSync
    private final boolean inheritPreviousEncoders
    private final boolean replaceOnly
    private final OutputContext outputContext

    boolean isInheritPreviousEncoders() {
        return inheritPreviousEncoders
    }

    boolean isReplaceOnly() {
        return replaceOnly
    }

    Writer getTopWriter() {
        return topWriter
    }

    Encoder getStaticEncoder() {
        return staticEncoder
    }

    Encoder getOutEncoder() {
        return outEncoder
    }

    Encoder getExpressionEncoder() {
        return expressionEncoder
    }

    Encoder getTaglibEncoder() {
        return taglibEncoder
    }

    Encoder getDefaultTaglibEncoder() {
        return defaultTaglibEncoder
    }

    boolean isAllowCreate() {
        return allowCreate
    }

    boolean isPushTop() {
        return pushTop
    }

    boolean isAutoSync() {
        return autoSync
    }

    OutputContext getOutputContext() {
        return outputContext
    }

    static class Builder {
        private Writer topWriter
        private Encoder staticEncoder
        private Encoder outEncoder
        private Encoder expressionEncoder
        private Encoder taglibEncoder
        private Encoder defaultTaglibEncoder
        private boolean allowCreate = true
        private boolean pushTop = true
        private boolean autoSync = true
        private OutputContext outputContext
        private boolean inheritPreviousEncoders = false
        private boolean replaceOnly = false

        Builder() {
        }

        Builder(OutputEncodingStackAttributes attributes) {
            this.topWriter = attributes.topWriter
            this.staticEncoder = attributes.staticEncoder
            this.outEncoder = attributes.outEncoder
            this.expressionEncoder = attributes.expressionEncoder
            this.taglibEncoder = attributes.taglibEncoder
            this.defaultTaglibEncoder = attributes.defaultTaglibEncoder
            this.allowCreate = attributes.allowCreate
            this.pushTop = attributes.pushTop
            this.autoSync = attributes.autoSync
            this.outputContext = attributes.outputContext
            this.inheritPreviousEncoders = attributes.inheritPreviousEncoders
            this.replaceOnly = attributes.replaceOnly
        }

        Builder topWriter(Writer topWriter) {
            this.topWriter = topWriter
            return this
        }

        Builder staticEncoder(Encoder staticEncoder) {
            this.staticEncoder = staticEncoder
            return this
        }

        Builder outEncoder(Encoder outEncoder) {
            this.outEncoder = outEncoder
            return this
        }

        Builder expressionEncoder(Encoder expressionEncoder) {
            this.expressionEncoder = expressionEncoder
            return this
        }

        Builder taglibEncoder(Encoder taglibEncoder) {
            this.taglibEncoder = taglibEncoder
            return this
        }

        Builder defaultTaglibEncoder(Encoder defaultTaglibEncoder) {
            this.defaultTaglibEncoder = defaultTaglibEncoder
            return this
        }

        Builder allowCreate(boolean allowCreate) {
            this.allowCreate = allowCreate
            return this
        }

        Builder pushTop(boolean pushTop) {
            this.pushTop = pushTop
            return this
        }

        Builder autoSync(boolean autoSync) {
            this.autoSync = autoSync
            return this
        }

        Builder inheritPreviousEncoders(boolean inheritPreviousEncoders) {
            this.inheritPreviousEncoders = inheritPreviousEncoders
            return this
        }

        Builder replaceOnly(boolean replaceOnly) {
            this.replaceOnly = replaceOnly
            return this
        }

        Builder outputContext(OutputContext outputContext) {
            this.outputContext = outputContext
            return this
        }

        OutputEncodingStackAttributes build() {
            return new OutputEncodingStackAttributes(this)
        }
    }

    private OutputEncodingStackAttributes(Builder builder) {
        this.topWriter = builder.topWriter
        this.staticEncoder = builder.staticEncoder
        this.outEncoder = builder.outEncoder
        this.taglibEncoder = builder.taglibEncoder
        this.defaultTaglibEncoder = builder.defaultTaglibEncoder
        this.expressionEncoder = builder.expressionEncoder
        this.allowCreate = builder.allowCreate
        this.pushTop = builder.pushTop
        this.autoSync = builder.autoSync
        this.outputContext = builder.outputContext
        this.inheritPreviousEncoders = builder.inheritPreviousEncoders
        this.replaceOnly = builder.replaceOnly
    }
}
