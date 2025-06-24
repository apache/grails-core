/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.build.dependencies;

import io.micronaut.core.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Scope {

    public static final Scope ANNOTATION_PROCESSOR = new Scope(Source.MAIN, Collections.singletonList(Phase.ANNOTATION_PROCESSING));
    public static final Scope BUILD = new Scope(Source.BUILD_SRC, Arrays.asList(Phase.BUILD));
    public static final Scope IMPLEMENTATION = new Scope(Source.MAIN, Arrays.asList(Phase.COMPILATION, Phase.RUNTIME));
    public static final Scope COMPILE_ONLY = new Scope(Source.MAIN, Collections.singletonList(Phase.COMPILATION));
    public static final Scope CONSOLE = new Scope(Source.MAIN, Collections.singletonList(Phase.CONSOLE));
    public static final Scope DEVELOPMENT_ONLY = new Scope(Source.MAIN, Collections.singletonList(Phase.DEVELOPMENT_ONLY));
    public static final Scope RUNTIME_ONLY = new Scope(Source.MAIN, Collections.singletonList(Phase.RUNTIME));
    public static final Scope TEST_IMPLEMENTATION = new Scope(Source.TEST, Arrays.asList(Phase.COMPILATION, Phase.RUNTIME));
    public static final Scope TEST_ANNOTATION_PROCESSOR = new Scope(Source.TEST, Collections.singletonList(Phase.ANNOTATION_PROCESSING));
    public static final Scope TEST_AND_DEVELOPMENT_ONLY = new Scope(Source.MAIN, Arrays.asList(Phase.DEVELOPMENT_ONLY, Phase.TEST_RUNTIME));
    public static final Scope TEST_COMPILE_ONLY = new Scope(Source.TEST, Collections.singletonList(Phase.COMPILATION));
    public static final Scope TEST_RUNTIME_ONLY = new Scope(Source.TEST, Collections.singletonList(Phase.RUNTIME));
    public static final Scope OPENREWRITE = new Scope(Source.MAIN, Collections.singletonList(Phase.OPENREWRITE));
    public static final Scope PROFILE = new Scope(Source.MAIN, Collections.singletonList(Phase.PROFILE));
    public static final Scope INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES = new Scope(Source.MAIN, Collections.singletonList(Phase.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES));
    public static final Scope CLASSPATH = new Scope(Source.BUILDSCRIPT, Collections.singletonList(Phase.BUILD));

    @NonNull
    private Source source;

    @NonNull
    private List<Phase> phases;

    public Scope(@NonNull Source source,
                 @NonNull List<Phase> phases) {
        this.source = source;
        this.phases = phases;
    }

    @NonNull
    public Source getSource() {
        return source;
    }

    public void setSource(@NonNull Source source) {
        this.source = source;
    }

    @NonNull
    public List<Phase> getPhases() {
        return phases;
    }

    public void setPhases(@NonNull List<Phase> phases) {
        this.phases = phases;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Scope scope = (Scope) o;
        return source == scope.source && phases.equals(scope.phases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, phases);
    }
}
