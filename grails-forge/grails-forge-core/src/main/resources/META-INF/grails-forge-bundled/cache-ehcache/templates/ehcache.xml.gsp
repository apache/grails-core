%{--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--}%
<?xml version="1.0" encoding="UTF-8"?>
<!--
    The caches of the application, for Ehcache 3. The cache manager of the Grails Cache plugin,
    grailsCacheManager in grails-app/conf/spring/resources.groovy, caches with them through JCache (JSR-107).
    A cache that is not configured here is created from the template that jsr107:defaults names when it is
    first used, so @Cacheable('anything') caches without a line here.
-->
<config xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns="http://www.ehcache.org/v3"
        xmlns:jsr107="http://www.ehcache.org/v3/jsr107"
        xsi:schemaLocation="http://www.ehcache.org/v3 https://www.ehcache.org/schema/ehcache-core.xsd
                            http://www.ehcache.org/v3/jsr107 https://www.ehcache.org/schema/ehcache-107-ext.xsd">

    <service>
        <jsr107:defaults default-template="default"/>
    </service>

    <!-- what every cache that is not configured below has -->
    <cache-template name="default">
        <expiry>
            <ttl unit="minutes">10</ttl>
        </expiry>
        <heap unit="entries">1000</heap>
    </cache-template>

    <!-- an example: the cache of @Cacheable('example'), which keeps fewer entries than the template -->
    <cache alias="example" uses-template="default">
        <heap unit="entries">100</heap>
    </cache>
</config>
