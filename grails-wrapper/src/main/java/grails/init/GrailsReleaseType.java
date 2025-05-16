/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.init;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type of releases that a Grails version may represent
 */
public enum GrailsReleaseType {
    RELEASE,
    RC,
    MILESTONE,
    SNAPSHOT;

    /**
     * @return true if this is a snapshot release
     */
    boolean isSnapshot() {
        return this == SNAPSHOT;
    }

    /**
     * @return this release type and all higher priority release types
     */
    public List<GrailsReleaseType> upTo() {
        return Arrays.stream(GrailsReleaseType.values())
                .filter(e -> e.ordinal() <= this.ordinal())
                .collect(Collectors.toList());
    }
}
