# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# for testing in a container that is similar to the grails github action linux build environment
# run this from the root of the project
# `docker build -t grails:testing -f etc/bin/Dockerfile . && docker run -it --rm -v $(pwd):/home/groovy/project grails:testing bash`
# Keep java version synced with .sdkmanrc and .github/workflows/release.yml ($JAVA_VERSION)
FROM bellsoft/liberica-openjdk-debian:21.0.7

USER root
RUN apt-get update && apt-get install -y curl unzip coreutils libdigest-sha-perl gpg vim sudo psmisc locales groovy rsync nano

# Secondary Liberica JDK for the Grails-Micronaut "island" (grails-micronaut,
# grails-micronaut-bom). Micronaut 5 platform GA targets JVM 25 bytecode, so
# those two artifacts cannot be built or reproduced on the primary JDK 21
# above. The verify scripts (etc/bin/verify-reproducible.sh,
# etc/bin/test-reproducible-builds.sh) switch JAVA_HOME to ${JDK_25_HOME} when
# building the island, and switch back to the default for everything else.
# Keep $JDK_25_VERSION synced with $JAVA_VERSION_MICRONAUT in
# .github/workflows/release.yml.
ENV JDK_25_VERSION=25.0.3+11
ENV JDK_25_HOME=/opt/liberica-jdk25
RUN set -eu; \
    DPKG_ARCH=$(dpkg --print-architecture); \
    case "${DPKG_ARCH}" in \
      amd64)   JDK_ARCH=linux-amd64;   JDK_SHA512=54e58ec3f34a20dcf6f0bd607e15c47d1f3c26ff1cfe1ecf107a862c3df7b58a9d39c0a8edf01038e83d838c28956b607e7382dda0059c04c2be5b9c0bdfa7c3 ;; \
      arm64)   JDK_ARCH=linux-aarch64; JDK_SHA512=fb22b6f50186d76e19adbf990d83de4c32fee940b1f1150756529c7722254da36ff9872c4cdd342346e59d2211ae9f41d73ddf79b612dce4d7294d15c37c9349 ;; \
      *) echo "Unsupported architecture for JDK 25 install: ${DPKG_ARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/liberica-jdk25.tar.gz "https://download.bell-sw.com/java/${JDK_25_VERSION}/bellsoft-jdk${JDK_25_VERSION}-${JDK_ARCH}.tar.gz"; \
    echo "${JDK_SHA512}  /tmp/liberica-jdk25.tar.gz" | sha512sum -c -; \
    mkdir -p "${JDK_25_HOME}"; \
    tar -xzf /tmp/liberica-jdk25.tar.gz -C "${JDK_25_HOME}" --strip-components=1; \
    rm /tmp/liberica-jdk25.tar.gz; \
    "${JDK_25_HOME}/bin/java" -version

RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=en_US.UTF-8

RUN useradd --system --create-home --home-dir /home/groovy groovy
RUN usermod -s /bin/bash -g root -G sudo groovy
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
USER groovy

WORKDIR /home/groovy
RUN mkdir -p /home/groovy/scripts/etc/bin && mkdir -p /home/groovy/scripts/gradle/wrapper && mkdir -p /home/groovy/grails-verify && mkdir -p /home/groovy/project
ADD --chown=groovy etc/bin /home/groovy/scripts/etc/bin
ADD --chown=groovy gradlew /home/groovy/scripts
ADD --chown=groovy KEYS /home/groovy/scripts
ADD --chown=groovy gradle/wrapper/gradle-wrapper.jar /home/groovy/scripts/gradle/wrapper
ADD --chown=groovy gradle/wrapper/gradle-wrapper.properties /home/groovy/scripts/gradle/wrapper

# Defensive line-ending normalization. The repository's .gitattributes pins
# shell scripts, gradlew, KEYS, and the Dockerfile to LF on every platform, but
# committers with a pre-existing local checkout under core.autocrlf=true may
# still feed CRLF into the build context. Linux refuses scripts with bash\r
# shebangs and verify-keys.sh would fail the byte-for-byte SVN comparison.
# Strip CRs from any text file that must be LF on Linux.
RUN find /home/groovy/scripts -type f \( -name '*.sh' -o -name 'gradlew' -o -name 'KEYS' -o -name '*.properties' \) -exec sed -i 's/\r$//' {} \;

ENV PATH="/home/groovy/scripts:/home/groovy/scripts/etc/bin:$PATH"
ENV CI=true
ENV LANG=C.UTF-8
ENV LC_ALL=en_US.UTF-8
ENV LC_CTYPE=en_US.UTF-8

CMD ["/bin/bash", "-ec", "while :; do echo '.'; sleep 1000 ; done"]