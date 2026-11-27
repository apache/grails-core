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
FROM debian:bookworm-slim

USER root
RUN apt-get update && apt-get install -y ca-certificates curl unzip coreutils libdigest-sha-perl gpg vim sudo psmisc locales groovy rsync nano

# The JDK is the archive actions/setup-java installs for the release (see "Setup JDK" in
# .github/workflows/release.yml), so the release is verified with the JDK build it was built with.
# Liberica's JDK images are not used: an image tag can be a later build of the same version, and the
# images leave out the JDK sources (lib/src.zip).
# The version and both checksums change with the java version in .sdkmanrc; its comment says how.
ARG LIBERICA_VERSION=21.0.12+10
ARG LIBERICA_SHA256_AMD64=23b48e59f47099597f84378c0a765c83588dcad7f6987eb2fef0d08e7ee19f96
ARG LIBERICA_SHA256_AARCH64=4d3c0090153dc74cadf40eee57ce6f8efdbe10b28e1420103c6cbe86982f039d
RUN set -eu; \
    case "$(dpkg --print-architecture)" in \
      amd64) JDK_ARCH=amd64; JDK_SHA256="${LIBERICA_SHA256_AMD64}" ;; \
      arm64) JDK_ARCH=aarch64; JDK_SHA256="${LIBERICA_SHA256_AARCH64}" ;; \
      *) echo "No Liberica JDK checksum for architecture $(dpkg --print-architecture)" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/jdk.tar.gz "https://github.com/bell-sw/Liberica/releases/download/${LIBERICA_VERSION}/bellsoft-jdk${LIBERICA_VERSION}-linux-${JDK_ARCH}.tar.gz"; \
    echo "${JDK_SHA256}  /tmp/jdk.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/java/liberica; \
    tar -xzf /tmp/jdk.tar.gz -C /opt/java/liberica --strip-components=1; \
    rm /tmp/jdk.tar.gz; \
    test -f /opt/java/liberica/lib/src.zip
ENV JAVA_HOME=/opt/java/liberica
ENV PATH="${JAVA_HOME}/bin:${PATH}"

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
