/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.taglib.encoder;

import org.grails.encoder.Encoder;

import java.io.Writer;

public class OutputEncodingStackAttributes {
    private final Writer topWriter;
    private final Encoder staticEncoder;
    private final Encoder outEncoder;
    private final Encoder expressionEncoder;
    private final Encoder taglibEncoder;
    private final Encoder defaultTaglibEncoder;
    private final boolean allowCreate;
    private final boolean pushTop;
    private final boolean autoSync;
    private final boolean inheritPreviousEncoders;
    private final boolean replaceOnly;
    private final OutputContext outputContext;

    public boolean isInheritPreviousEncoders() {
        return inheritPreviousEncoders;
    }
    
    public boolean isReplaceOnly() {
        return replaceOnly;
    }

    public Writer getTopWriter() {
        return topWriter;
    }

    public Encoder getStaticEncoder() {
        return staticEncoder;
    }

    public Encoder getOutEncoder() {
        return outEncoder;
    }

    public Encoder getExpressionEncoder() {
        return expressionEncoder;
    }

    public Encoder getTaglibEncoder() {
        return taglibEncoder;
    }

    public Encoder getDefaultTaglibEncoder() {
        return defaultTaglibEncoder;
    }

    public boolean isAllowCreate() {
        return allowCreate;
    }

    public boolean isPushTop() {
        return pushTop;
    }

    public boolean isAutoSync() {
        return autoSync;
    }

    public OutputContext getOutputContext() {
        return outputContext;
    }

    public static class Builder {
        private Writer topWriter;
        private Encoder staticEncoder;
        private Encoder outEncoder;
        private Encoder expressionEncoder;
        private Encoder taglibEncoder;
        private Encoder defaultTaglibEncoder;
        private boolean allowCreate=true;
        private boolean pushTop=true;
        private boolean autoSync=true;
        private OutputContext outputContext;
        private boolean inheritPreviousEncoders=false;
        private boolean replaceOnly=false;

        public Builder() {
        }

        public Builder(OutputEncodingStackAttributes attributes) {
            this.topWriter = attributes.topWriter;
            this.staticEncoder = attributes.staticEncoder;
            this.outEncoder = attributes.outEncoder;
            this.expressionEncoder = attributes.expressionEncoder;
            this.taglibEncoder = attributes.taglibEncoder;
            this.defaultTaglibEncoder = attributes.defaultTaglibEncoder;
            this.allowCreate = attributes.allowCreate;
            this.pushTop = attributes.pushTop;
            this.autoSync = attributes.autoSync;
            this.outputContext = attributes.outputContext;
            this.inheritPreviousEncoders = attributes.inheritPreviousEncoders;
            this.replaceOnly = attributes.replaceOnly;
        }

        public Builder topWriter(Writer topWriter) {
            this.topWriter = topWriter;
            return this;
        }

        public Builder staticEncoder(Encoder staticEncoder) {
            this.staticEncoder = staticEncoder;
            return this;
        }

        public Builder outEncoder(Encoder outEncoder) {
            this.outEncoder = outEncoder;
            return this;
        }

        public Builder expressionEncoder(Encoder expressionEncoder) {
            this.expressionEncoder = expressionEncoder;
            return this;
        }

        public Builder taglibEncoder(Encoder taglibEncoder) {
            this.taglibEncoder = taglibEncoder;
            return this;
        }

        public Builder defaultTaglibEncoder(Encoder defaultTaglibEncoder) {
            this.defaultTaglibEncoder = defaultTaglibEncoder;
            return this;
        }

        public Builder allowCreate(boolean allowCreate) {
            this.allowCreate = allowCreate;
            return this;
        }

        public Builder pushTop(boolean pushTop) {
            this.pushTop = pushTop;
            return this;
        }

        public Builder autoSync(boolean autoSync) {
            this.autoSync = autoSync;
            return this;
        }

        public Builder inheritPreviousEncoders(boolean inheritPreviousEncoders) {
            this.inheritPreviousEncoders = inheritPreviousEncoders;
            return this;
        }
        
        public Builder replaceOnly(boolean replaceOnly) {
            this.replaceOnly = replaceOnly;
            return this;
        }

        public Builder outputContext(OutputContext outputContext) {
            this.outputContext = outputContext;
            return this;
        }

        public OutputEncodingStackAttributes build() {
            return new OutputEncodingStackAttributes(this);
        }
    }

    private OutputEncodingStackAttributes(Builder builder) {
        this.topWriter = builder.topWriter;
        this.staticEncoder = builder.staticEncoder;
        this.outEncoder = builder.outEncoder;
        this.taglibEncoder = builder.taglibEncoder;
        this.defaultTaglibEncoder = builder.defaultTaglibEncoder;
        this.expressionEncoder = builder.expressionEncoder;
        this.allowCreate = builder.allowCreate;
        this.pushTop = builder.pushTop;
        this.autoSync = builder.autoSync;
        this.outputContext = builder.outputContext;
        this.inheritPreviousEncoders = builder.inheritPreviousEncoders;
        this.replaceOnly = builder.replaceOnly;
    }
}
