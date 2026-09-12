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
package org.grails.forge.feature.build.gradle;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Dockerfile {
    @Nullable
    private String baseImage;

    @Nullable
    private List<String> args;

    public Dockerfile(@Nonnull String baseImage, @Nonnull List<String> args) {
        this.baseImage = baseImage;
        this.args = args;
    }

    @Nullable
    public String getBaseImage() {
        return baseImage;
    }

    @Nullable
    public List<String> getArgs() {
        return args;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseImage;
        private List<String> args;

        @Nonnull
        public Builder baseImage(String baseImage) {
            this.baseImage = baseImage;
            return this;
        }

        @Nonnull
        public Builder arg(String arg) {
            if (args == null) {
                args = new ArrayList<>();
            }
            args.add(arg);
            return this;
        }

        @Nonnull
        public Builder args(List<String> args) {
            this.args = args;
            return this;
        }

        @Nonnull
        public Dockerfile build() {
            return new Dockerfile(baseImage, args);
        }
    }
}
