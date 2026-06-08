<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Grails Spring Security ACL Plugin
==================================

See [documentation](https://apache.github.io/grails-spring-security/latest/acl-plugin/guide) for further information.

## v5.0.0 changes

### Caching

The default cache manager has changed to
[JCacheCacheManager](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/cache/jcache/JCacheCacheManager.html).

### Method parameter discovery

The behavior of parameter discovery has changed to align with
[Spring Security 6 default](https://docs.spring.io/spring-security/site/docs/6.4.1/api//org/springframework/security/core/parameters/DefaultSecurityParameterNameDiscoverer.html)
behavior.  This may require code changes if you are utilizing ACL
annotations that reference method parameters.  You will need to add the
[P](https://docs.spring.io/spring-security/site/docs/6.4.1/api/org/springframework/security/core/parameters/P.html)
annotation to reference method parameters.  This is documented in the
Spring Security reference doc under the
[Using Method Parameters](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html#using_method_parameters)
section.

Previously if you had code similar to:
```
@PreAuthorize("hasPermission(#contract, 'write')")
public void updateContact(Contact contact) {
    ...
}
```

This should be changed to:

```
import org.springframework.security.core.parameters.P

@PreAuthorize("hasPermission(#contract, 'write')")
public void updateContact(@P("contract") Contact contact) {
    ...
}
```

Since parameter `contract` is referenced in the `@PreAuthorize` annotation, it
should now be annotated with `@P`.
