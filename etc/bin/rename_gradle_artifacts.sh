#!/bin/bash

#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

set -euo pipefail

# This script updates Gradle build files by replacing old artifact coordinates with the new ones,
# as defined in the reference table at:
#   https://github.com/apache/grails-core/blob/7.0.x/RENAME.md
#
# Usage:
#   ./rename_gradle_artifacts.sh [directory]
#
# If no directory is provided, the current directory is used.
#
# The script searches for files ending in .gradle recursively and applies
# a series of sed substitutions for each mapping.

if [[ "$(uname)" == "Darwin" ]]; then
    SED_INPLACE=(-i '' -E)
else
    SED_INPLACE=(-i -E)
fi

declare -a mappings=()
declare -a excluded_mappings=()
while getopts "pdvsqrtcfgl:" flag; do
  if [ "${flag}" = "l" ]; then
    echo "Mapping artifacts in directory"
    DIR="${OPTARG}"
  elif [ "${flag}" = "g" ]; then
    echo "Mapping grails-core artifacts"
    declare -a core_mappings=(
    "org[.]grails[.]plugins:events|org.apache.grails:grails-events"
    "org[.]grails[.]plugins:converters|org.apache.grails:grails-converters"
    "org[.]grails[.]plugins:async|org.apache.grails:grails-async"
    "org[.]grails:grails-web-url-mappings|org.apache.grails.web:grails-web-url-mappings"
    "org[.]grails:grails-web-mvc|org.apache.grails.web:grails-web-mvc"
    "org[.]grails:grails-web-databinding|org.apache.grails.web:grails-web-databinding"
    "org[.]grails:grails-web-common|org.apache.grails.web:grails-web-common"
    "org[.]grails:grails-web-boot|org.apache.grails:grails-web-boot"
    "org[.]grails:grails-web|org.apache.grails.web:grails-web"
    "org[.]grails:grails-testing-support|org.apache.grails.testing:grails-testing-support-core"
    "org[.]grails:grails-test|org.apache.grails.testing:grails-test-core"
    "org[.]grails:grails-spring|org.apache.grails:grails-spring"
    "org[.]grails:grails-shell|org.apache.grails:grails-shell-cli"
    "org[.]grails:grails-plugin-validation|org.apache.grails:grails-validation"
    "org[.]grails:grails-plugin-url-mappings|org.apache.grails:grails-url-mappings"
    "org[.]grails:grails-plugin-services|org.apache.grails:grails-services"
    "org[.]grails:grails-plugin-rest|org.apache.grails:grails-rest-transforms"
    "org[.]grails:grails-plugin-mimetypes|org.apache.grails:grails-mimetypes"
    "org[.]grails:grails-plugin-interceptors|org.apache.grails:grails-interceptors"
    "org[.]grails:grails-plugin-i18n|org.apache.grails:grails-i18n"
    "org[.]grails:grails-plugin-domain-class|org.apache.grails:grails-domain-class"
    "org[.]grails:grails-plugin-datasource|org.apache.grails:grails-datasource"
    "org[.]grails:grails-plugin-databinding|org.apache.grails:grails-databinding"
    "org[.]grails:grails-plugin-controllers|org.apache.grails:grails-controllers"
    "org[.]grails:grails-plugin-codecs|org.apache.grails:grails-codecs"
    "org[.]grails:grails-logging|org.apache.grails:grails-logging"
    "org[.]grails:grails-gradle-model|org.apache.grails.gradle:grails-gradle-model"
    "org[.]grails:grails-events-transform|org.apache.grails.events:grails-events-transforms"
    "org[.]grails:grails-events-spring|org.apache.grails.events:grails-events-spring"
    "org[.]grails:grails-events-rxjava3|org.apache.grails.events:grails-events-rxjava3"
    "org[.]grails:grails-events-rxjava2|org.apache.grails.events:grails-events-rxjava2"
    "org[.]grails:grails-events-rxjava|org.apache.grails.events:grails-events-rxjava"
    "org[.]grails:grails-events-gpars|org.apache.grails.events:grails-events-gpars"
    "org[.]grails:grails-events-compat|org.apache.grails.events:grails-events-compat"
    "org[.]grails:grails-events|org.apache.grails.events:grails-events-core"
    "org[.]grails:grails-encoder|org.apache.grails.web:grails-encoder"
    "org[.]grails:grails-docs|org.apache.grails:grails-docs-core"
    "org[.]grails:grails-dependencies|org.apache.grails:grails-dependencies"
    "org[.]grails:grails-databinding|org.apache.grails.databinding:grails-databinding-core"
    "org[.]grails:grails-core|org.apache.grails:grails-core"
    "org[.]grails:grails-console|org.apache.grails:grails-console"
    "org[.]grails:grails-codecs|org.apache.grails.codecs:grails-codecs-core"
    "org[.]grails:grails-bootstrap|org.apache.grails.bootstrap:grails-bootstrap"
    "org[.]grails:grails-bom|org.apache.grails:grails-bom"
    "org[.]grails:grails-async-rxjava3|org.apache.grails.async:grails-async-rxjava3"
    "org[.]grails:grails-async-rxjava2|org.apache.grails.async:grails-async-rxjava2"
    "org[.]grails:grails-async-rxjava|org.apache.grails.async:grails-async-rxjava"
    "org[.]grails:grails-async-gpars|org.apache.grails.async:grails-async-gpars"
    "org[.]grails:grails-async|org.apache.grails.async:grails-async-core"
    )
      declare -a excluded_core_mappings=(
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-testing-support['\"]|exclude module:'grails-testing-support-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-test['\"]|exclude module:'grails-test-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-shell['\"]|exclude module:'grails-shell-cli'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-validation['\"]|exclude module:'grails-validation'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-url-mappings['\"]|exclude module:'grails-url-mappings'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-services['\"]|exclude module:'grails-services'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-rest['\"]|exclude module:'grails-rest-transforms'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-mimetypes['\"]|exclude module:'grails-mimetypes'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-interceptors['\"]|exclude module:'grails-interceptors'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-i18n['\"]|exclude module:'grails-i18n'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-domain-class['\"]|exclude module:'grails-domain-class'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-datasource['\"]|exclude module:'grails-datasource'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-databinding['\"]|exclude module:'grails-databinding'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-controllers['\"]|exclude module:'grails-controllers'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-codecs['\"]|exclude module:'grails-codecs'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-gradle-model['\"]|exclude module:'grails-gradle-model'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-transform['\"]|exclude module:'grails-events-transforms'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-spring['\"]|exclude module:'grails-events-spring'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-rxjava['\"]|exclude module:'grails-events-rxjava'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-rxjava3['\"]|exclude module:'grails-events-rxjava3'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-rxjava2['\"]|exclude module:'grails-events-rxjava2'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-gpars['\"]|exclude module:'grails-events-gpars'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events-compat['\"]|exclude module:'grails-events-compat'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-events['\"]|exclude module:'grails-events-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-encoder['\"]|exclude module:'grails-encoder'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-docs['\"]|exclude module:'grails-docs-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-dependencies['\"]|exclude module:'grails-dependencies'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-databinding['\"]|exclude module:'grails-databinding-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-core['\"]|exclude module:'grails-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-console['\"]|exclude module:'grails-console'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-codecs['\"]|exclude module:'grails-codecs-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-bootstrap['\"]|exclude module:'grails-bootstrap'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-bom['\"]|exclude module:'grails-bom'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-async-rxjava['\"]|exclude module:'grails-async-rxjava'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-async-rxjava3['\"]|exclude module:'grails-async-rxjava3'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-async-rxjava2['\"]|exclude module:'grails-async-rxjava2'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-async-gpars['\"]|exclude module:'grails-async-gpars'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-async['\"]|exclude module:'grails-async-core'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]events['\"]|exclude module:'grails-events'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]converters['\"]|exclude module:'grails-converters'"
    "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]async['\"]|exclude module:'grails-async'"
      )
    mappings+=("${core_mappings[@]}")
    excluded_mappings+=("${excluded_core_mappings[@]}")
  elif [ "${flag}" = "v" ]; then
    echo "Mapping grails-views artifacts"
    declare -a views_mappings=(
      "org[.]grails[.]plugins:views-json|org.apache.grails:grails-views-gson"
      "org[.]grails[.]plugins:scaffolding|org.apache.grails:grails-scaffolding"
      "org[.]grails[.]plugins:gsp|org.apache.grails:grails-gsp"
      "org[.]grails[.]plugins:fields|org.apache.grails:grails-fields"
      "org[.]grails:views-markup|org.apache.grails:grails-views-markup"
      "org[.]grails:views-json-testing-support|org.apache.grails:grails-testing-support-views-gson"
      "org[.]grails:views-core|org.apache.grails.views:grails-views-core"
      "org[.]grails:grails-web-testing-support|org.apache.grails:grails-testing-support-web"
      "org[.]grails:grails-web-taglib|org.apache.grails.views:grails-web-taglib"
      "org[.]grails:grails-web-jsp|org.apache.grails.views:grails-web-jsp"
      "org[.]grails:grails-web-gsp-taglib|org.apache.grails.views:grails-web-gsp-taglib"
      "org[.]grails:grails-web-gsp|org.apache.grails.views:grails-web-gsp"
      "org[.]grails:grails-taglib|org.apache.grails.views:grails-taglib"
      "org[.]grails:grails-plugin-sitemesh3|org.apache.grails:grails-sitemesh3"
      "org[.]grails:grails-gsp|org.apache.grails.views:grails-gsp-core"
    )
      declare -a excluded_gsp_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]views-markup['\"]|exclude module:'grails-views-markup'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]views-json['\"]|exclude module:'grails-views-gson'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]views-json-testing-support['\"]|exclude module:'grails-testing-support-views-gson'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]views-core['\"]|exclude module:'grails-views-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]scaffolding['\"]|exclude module:'grails-scaffolding'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]gsp['\"]|exclude module:'grails-gsp'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-web-testing-support['\"]|exclude module:'grails-testing-support-web'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-plugin-sitemesh3['\"]|exclude module:'grails-sitemesh3'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-gsp['\"]|exclude module:'grails-gsp-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]fields['\"]|exclude module:'grails-fields'"
      )
    mappings+=("${views_mappings[@]}")
    excluded_mappings+=("${excluded_gsp_mappings[@]}")
  elif [ "${flag}" = "d" ]; then
    echo "Mapping grails-data artifacts"
    declare -a gorm_mappings=(
      "org[.]grails[.]plugins:views-json-templates|org.apache.grails:grails-data-mongodb-gson-templates"
      "org[.]grails[.]plugins:mongodb|org.apache.grails:grails-data-mongodb"
      "org[.]grails[.]plugins:hibernate6|org.apache.grails:grails-data-hibernate6"
      "org[.]grails[.]plugins:hibernate5|org.apache.grails:grails-data-hibernate5"
      "org[.]grails[.]plugins:database-migration|org.apache.grails:grails-data-hibernate6-dbmigration"
      "org[.]grails[.]plugins:database-migration|org.apache.grails:grails-data-hibernate5-dbmigration"
      "org[.]grails[.]tck[.]tests:tck|org.apache.grails.data:grails-datamapping-tck-tests"
      "org[.]grails[.]tck[.]domains:tck-domains|org.apache.grails.data:grails-datamapping-tck-domains"
      "org[.]grails[.]tck[.]base:tck-base|org.apache.grails.data:grails-datamapping-tck-base"
      "org[.]grails:grails-gorm-testing-support|org.apache.grails:grails-testing-support-datamapping"
      "org[.]grails:grails-datastore-web|org.apache.grails.data:grails-datastore-web"
      "org[.]grails:grails-datastore-gorm-validation|org.apache.grails.data:grails-datamapping-validation"
      "org[.]grails:grails-datastore-gorm-support|org.apache.grails.data:grails-datamapping-support"
      "org[.]grails:grails-datastore-gorm-simple|org.apache.grails.data:grails-data-simple"
      "org[.]grails:grails-datastore-gorm-mongodb-bson|org.apache.grails.data:grails-data-mongodb-bson"
      "org[.]grails:grails-datastore-gorm-mongodb-ext|org.apache.grails.data:grails-data-mongodb-ext"
      "org[.]grails:grails-datastore-gorm-mongodb|org.apache.grails.data:grails-data-mongodb-core"
      "org[.]grails:grails-datastore-gorm-hibernate6|org.apache.grails.data:grails-data-hibernate6-core"
      "org[.]grails:grails-datastore-gorm-hibernate5|org.apache.grails.data:grails-data-hibernate5-core"
      "org[.]grails:grails-datastore-gorm-async|org.apache.grails.data:grails-datamapping-async"
      "org[.]grails:grails-datastore-gorm|org.apache.grails.data:grails-datamapping-core"
      "org[.]grails:grails-datastore-gorm-tck|org.apache.grails.data:grails-datamapping-tck-tests"
      "org[.]grails:grails-datastore-core|org.apache.grails.data:grails-datastore-core"
      "org[.]grails:grails-datastore-async|org.apache.grails.data:grails-datastore-async"
      "org[.]grails:gorm-mongodb-spring-boot|org.apache.grails:grails-data-mongodb-spring-boot"
      "org[.]grails:gorm-hibernate6-spring-boot|org.apache.grails:grails-data-hibernate6-spring-boot"
      "org[.]grails:gorm-hibernate5-spring-boot|org.apache.grails:grails-data-hibernate5-spring-boot"
    )
    declare -a excluded_gorm_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]views-json-templates['\"]|exclude module:'grails-data-mongodb-gson-templates'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]tck-tests['\"]|exclude module:'grails-datamapping-tck-tests'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]tck-domains['\"]|exclude module:'grails-datamapping-tck-domains'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]tck-base['\"]|exclude module:'grails-datamapping-tck-base'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]mongodb['\"]|exclude module:'grails-data-mongodb'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]hibernate6['\"]|exclude module:'grails-data-hibernate6'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]hibernate5['\"]|exclude module:'grails-data-hibernate5'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-gorm-testing-support['\"]|exclude module:'grails-testing-support-datamapping'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-web['\"]|exclude module:'grails-datastore-web'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-validation['\"]|exclude module:'grails-datamapping-validation'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-support['\"]|exclude module:'grails-datamapping-support'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-simple['\"]|exclude module:'grails-data-simple'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-mongodb['\"]|exclude module:'grails-data-mongodb-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-mongodb-ext['\"]|exclude module:'grails-data-mongodb-ext'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-hibernate6['\"]|exclude module:'grails-data-hibernate6-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-hibernate5['\"]|exclude module:'grails-data-hibernate5-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm-async['\"]|exclude module:'grails-datamapping-async'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-gorm['\"]|exclude module:'grails-datamapping-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-core['\"]|exclude module:'grails-datastore-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-datastore-async['\"]|exclude module:'grails-datastore-async'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]gorm-mongodb-spring-boot['\"]|exclude module:'grails-data-mongodb-spring-boot'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]gorm-hibernate6-spring-boot['\"]|exclude module:'grails-data-hibernate6-spring-boot'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]gorm-hibernate5-spring-boot['\"]|exclude module:'grails-data-hibernate5-spring-boot'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]database-migration['\"]|exclude module:'grails-data-hibernate5-dbmigration'"
    )
    mappings+=("${gorm_mappings[@]}")
    excluded_mappings+=("${excluded_gorm_mappings[@]}")
  elif [ "${flag}" = "t" ]; then
    echo "Mapping grails-geb artifacts"
    declare -a geb_mappings=(
      "org[.]grails[.]plugins:geb|org.apache.grails:grails-geb"
    )
    declare -a excluded_geb_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]geb['\"]|exclude module:'grails-geb'"
    )
    mappings+=("${geb_mappings[@]}")
    excluded_mappings+=("${excluded_geb_mappings[@]}")
  elif [ "${flag}" = "c" ]; then
    echo "Mapping grails-cache artifacts"
    declare -a cache_mappings=(
      "org[.]grails[.]plugins:cache|org.apache.grails:grails-cache"
    )
    declare -a excluded_cache_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]cache['\"]|exclude module:'grails-cache'"
    )
    mappings+=("${cache_mappings[@]}")
    excluded_mappings+=("${excluded_cache_mappings[@]}")
  elif [ "${flag}" = "f" ]; then
    echo "Mapping grails-forge artifacts"
    declare -a forge_mappings=(
      "org[.]grails[.]forge:grails-forge-core|org.apache.grails.forge:grails-forge-core"
      "org[.]grails[.]forge:grails-forge-api|org.apache.grails.forge:grails-forge-api"
      "org[.]grails[.]forge:grails-cli|org.apache.grails.forge:grails-forge-cli"
    )
    declare -a excluded_forge_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-forge-core['\"]|exclude module:'grails-forge-core'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-forge-api['\"]|exclude module:'grails-forge-api'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]grails-cli['\"]|exclude module:'grails-forge-cli'"
    )
    mappings+=("${forge_mappings[@]}")
    excluded_mappings+=("${excluded_forge_mappings[@]}")
  elif [ "${flag}" = "q" ]; then
    echo "Mapping grails-quartz artifacts"
    declare -a quartz_mappings=(
      "org[.]grails[.]plugins:quartz|org.apache.grails:grails-quartz"
    )
    declare -a excluded_quartz_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]quartz['\"]|exclude module:'grails-quartz'"
    )
    mappings+=("${quartz_mappings[@]}")
    excluded_mappings+=("${excluded_quartz_mappings[@]}")
  elif [ "${flag}" = "r" ]; then
    echo "Mapping grails-redis artifacts"
    declare -a redis_mappings=(
      "org[.]grails[.]plugins:grails-redis|org.apache.grails:grails-redis"
    )
    mappings+=("${redis_mappings[@]}")
  elif [ "${flag}" = "s" ]; then
    echo "Mapping grails-security artifacts"
    declare -a security_mappings=(
      "org[.]grails[.]plugins:spring-security-ui|org.apache.grails:grails-spring-security-ui"
      "org[.]grails[.]plugins:spring-security-rest-testapp-profile|org.apache.grails.profiles:spring-security-rest-testapp"
      "org[.]grails[.]plugins:spring-security-rest-redis|org.apache.grails:grails-spring-security-rest-redis"
      "org[.]grails[.]plugins:spring-security-rest-memcached|org.apache.grails:grails-spring-security-rest-memcached"
      "org[.]grails[.]plugins:spring-security-rest-grailscache|org.apache.grails:grails-spring-security-grails-cache"
      "org[.]grails[.]plugins:spring-security-rest-gorm|org.apache.grails:grails-spring-security-rest-datamapping"
      "org[.]grails[.]plugins:spring-security-rest|org.apache.grails:grails-spring-security-rest"
      "org[.]grails[.]plugins:spring-security-oauth2|org.apache.grails:grails-spring-security-oauth2"
      "org[.]grails[.]plugins:spring-security-ldap|org.apache.grails:grails-spring-security-ldap"
      "org[.]grails[.]plugins:spring-security-core|org.apache.grails:grails-spring-security"
      "org[.]grails[.]plugins:spring-security-cas|org.apache.grails:grails-spring-security-cas"
      "org[.]grails[.]plugins:spring-security-acl|org.apache.grails:grails-spring-security-acl"
    )
    declare -a excluded_security_mappings=(
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-ui['\"]|exclude module:'grails-spring-security-ui'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest['\"]|exclude module:'grails-spring-security-rest'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest-testapp-profile['\"]|exclude module:'spring-security-rest-testapp'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest-redis['\"]|exclude module:'grails-spring-security-rest-redis'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest-memcached['\"]|exclude module:'grails-spring-security-rest-memcached'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest-grailscache['\"]|exclude module:'grails-spring-security-grails-cache'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-rest-gorm['\"]|exclude module:'grails-spring-security-rest-datamapping'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-oauth2['\"]|exclude module:'grails-spring-security-oauth2'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-ldap['\"]|exclude module:'grails-spring-security-ldap'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-core['\"]|exclude module:'grails-spring-security'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-cas['\"]|exclude module:'grails-spring-security-cas'"
      "exclude[[:space:]]+module[[:space:]]*:[[:space:]]*['\"]spring-security-acl['\"]|exclude module:'grails-spring-security-acl'"
    )
    mappings+=("${security_mappings[@]}")
    excluded_mappings+=("${excluded_security_mappings[@]}")
  elif [ "${flag}" = "p" ]; then
    echo "Mapping grails-profile artifacts"
    declare -a profile_mappings=(
        "org[.]grails[.]profiles:web-plugin|org.apache.grails.profiles:web-plugin"
        "org[.]grails[.]profiles:web|org.apache.grails.profiles:web"
        "org[.]grails[.]profiles:vue|org.apache.grails.profiles:vue"
        "org[.]grails[.]profiles:rest-api-plugin|org.apache.grails.profiles:rest-api-plugin"
        "org[.]grails[.]profiles:rest-api|org.apache.grails.profiles:rest-api"
        "org[.]grails[.]profiles:react|org.apache.grails.profiles:react"
        "org[.]grails[.]profiles:profile|org.apache.grails.profiles:profile"
        "org[.]grails[.]profiles:plugin|org.apache.grails.profiles:plugin"
        "org[.]grails[.]profiles:base|org.apache.grails.profiles:base"
        "org[.]grails[.]profiles:angular|org.apache.grails.profiles:angular"
    )
    mappings+=("${profile_mappings[@]}")
  fi
done

# default to the current directory
DIR=${DIR:-.}

if [ ${#mappings[@]} -eq 0 ]; then
    echo "Error: no mappings were specified" >&2
    exit 1
fi

files=$(find "${DIR}" -type f \( -name "*.gradle" \))

if [ -z "${files}" ]; then
    echo "No Gradle files found in directory: ${DIR}"
    exit 0
fi

for file in ${files}; do
    echo "Processing file: ${file}"
    for mapping in "${mappings[@]}"; do
        IFS='|' read -r old_pattern new_replacement <<< "${mapping}"
        # echo "     Using regex '${old_pattern}' to replace with '${new_replacement}' in '${file}'"
        sed "${SED_INPLACE[@]}" "s/${old_pattern}([:'\"])/${new_replacement}\1/g" "${file}"
    done
    for mapping in "${excluded_mappings[@]}"; do
        IFS='|' read -r old_pattern new_replacement <<< "${mapping}"
        # echo "     Using excluded regex '${old_pattern}' to replace with '${new_replacement}' in '${file}'"
        sed "${SED_INPLACE[@]}" "s/${old_pattern}/${new_replacement}/g" "${file}"
    done
done

echo "Artifact paths updated."
