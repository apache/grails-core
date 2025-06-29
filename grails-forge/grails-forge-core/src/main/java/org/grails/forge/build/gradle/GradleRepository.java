/*
 * Copyright 2025 original authors
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
package org.grails.forge.build.gradle;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface GradleRepository extends Ordered {
    @NonNull
    String toSnippet(String basePadding);

    static Set<GradleRepository> getDefaultRepositories(String grailsVersion) {
        Set<GradleRepository> repositories = new HashSet<>();

        String overrideRepo = System.getenv("GRAILS_REPO_URL");
        if (overrideRepo != null && !overrideRepo.isEmpty()) {
            repositories.add(new DefaultGradleRepository(0, overrideRepo));
        }
        repositories.add(new MavenCentralRepository(repositories.size()));
        repositories.add(new DefaultGradleRepository(repositories.size(), "https://repo.grails.org/grails/restricted"));
        if (grailsVersion.endsWith("SNAPSHOT")) {
            repositories.add(new DefaultGradleRepository(
                    repositories.size(),
                    "https://repository.apache.org/content/groups/snapshots",
                    null,
                    List.of(
                            new VersionRegexRepoFilter(
                                    "org[.]apache[.]((grails)|(groovy)).*", ".*", ".*-SNAPSHOT"
                            )
                    )
            ));
        }
        repositories.add(new DefaultGradleRepository(
                repositories.size(),
                "https://central.sonatype.com/repository/maven-snapshots",
                null,
                List.of(
                        new VersionRegexRepoFilter(
                                "cloud[.]wondrify", ".*", ".*-SNAPSHOT"
                        )
                )
        ));

        return repositories;
    }
}
