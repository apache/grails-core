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
FROM bellsoft/liberica-openjdk-debian:17.0.14

USER root
RUN apt-get update && apt-get install -y curl unzip coreutils libdigest-sha-perl gpg vim sudo psmisc locales groovy rsync

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
ENV PATH="/home/groovy/scripts:/home/groovy/scripts/etc/bin:$PATH"
ENV CI=true
ENV LANG=C.UTF-8
ENV LC_ALL=en_US.UTF-8
ENV LC_CTYPE=en_US.UTF-8

CMD ["/bin/bash", "-ec", "while :; do echo '.'; sleep 1000 ; done"]