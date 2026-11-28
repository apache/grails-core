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
<% if (features.contains('cache-ehcache')) { %>
import org.springframework.cache.jcache.JCacheManagerFactoryBean

import ${project.packageName}.JCacheGrailsCacheManager

<% } %>
// Place your Spring DSL code here
beans = {
<% if (features.contains('cache-ehcache')) { %>
    // the caches of grails-app/conf/ehcache.xml, through JCache (JSR-107)
    jCacheManager(JCacheManagerFactoryBean) {
        cacheManagerUri = 'classpath:ehcache.xml'
    }
    // the cache manager of the Grails Cache plugin, which its annotations, tags and admin service cache with
    grailsCacheManager(JCacheGrailsCacheManager, ref('jCacheManager'))
<% } %>
}