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

## Grails Testing Support Http Client

Provides fluent HTTP client testing support for Grails integration and functional tests.

### Required Libraries

To use HTTP client testing support in your integration tests, add the following dependency:

```gradle
dependencies {
    integrationTestImplementation 'org.apache.grails:grails-testing-support-http-client'
}
```

### Basic Usage

In Grails integration tests, implement the `HttpClientSupport` trait:

```groovy
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class HealthSpec extends Specification implements HttpClientSupport {

    void 'health endpoint responds OK'() {
        expect:
        http('/health').expectContains(200, 'UP')
    }
}
```

Besides `http(...)` for `GET`, the trait also includes convenience helpers for other verbs such as
`httpHead(...)`, `httpTrace(...)`, `httpDelete(...)`, `httpOptions(...)`, `httpPost(...)`, `httpPut(...)`, and `httpPatch(...)`.

### Custom JSON Parsing

When response JSON needs non-default `JsonSlurper` behavior, configure it fluently on the response wrapper:

```groovy
import groovy.json.JsonParserType

http('/config')
    .withJsonSlurper(parserType: JsonParserType.LAX, checkDates: true)
    .expectJsonContains('{featureFlag:true}')
```

Supported named options mirror the `JsonSlurper` settings exposed by `JsonUtils.JsonSlurperConfig`, including
`parserType`, `checkDates`, `chop`, `lazyChop`, and `maxSizeForInMemory`.

### Custom XML Parsing

Response XML parsing uses a secure default `XmlSlurper` configuration. It is namespace-aware, non-validating,
allows inline `DOCTYPE` declarations, and disables external entity expansion plus external DTD loading.

When a test needs different XML parsing behavior, override it fluently on the response wrapper:

```groovy
import groovy.xml.XmlSlurper

http('/feed')
    .withXmlSlurper(factory: { new XmlSlurper(false, false) })
    .xml()
```

`withXmlSlurper(...)` accepts an `XmlUtils.SlurperConfig`. The default configuration is secure; the
`factory` hook is available for tests that need a fully custom parser instance.

### JSON payload formatting

JSON request bodies can be sent in three ways depending on how much formatting control you need.

For exact control over the payload text, pass a pre-rendered JSON string to the request helper:

```groovy
httpPostJson('/products', '{"name":"Widget","tags":["new","sale"]}')
```

For the common case, use the `http[Post|Put|Patch]Json` helpers with a `Map`. They serialize the payload with
Groovy's default `JsonOutput.toJson(...)` behavior:

```groovy
httpPostJson('/products', [name: 'Widget', qty: 2])
```

When you want custom JSON formatting, pass a `JsonGenerator` alongside the `Map` payload. This lets you control
rendering details such as null handling, field exclusions, converters, and date formatting:

```groovy
import groovy.json.JsonGenerator

def jsonGenerator = new JsonGenerator.Options()
    .excludeNulls()
    .dateFormat('yyyy-MM-dd')
    .build()

httpPutJson('/products/1', jsonGenerator, [
    name: 'Widget',
    discontinuedAt: null,
    releaseDate: new Date()
])
```

The same `JsonGenerator` overload pattern is available on `httpPostJson(...)`, `httpPutJson(...)`, and
`httpPatchJson(...)`, including the variants that accept request headers and an explicit `HttpClient`.

### XML formatting

XML request bodies can be generated with Groovy MarkupBuilder DSL. For the `http[Post|Patch|Put]Xml` methods,
`HttpClientSupport` accepts an optional `XmlUtils.Format` instance, which constructor takes named params to
configure the format:

```groovy
import org.apache.grails.testing.http.client.utils.XmlUtils

httpPostXml('/products', new XmlUtils.Format(
    omitDeclaration: false,
    prettyPrint: true,
    indent: '  ',
    lineSeparator: '\n',
    doctype: '<!DOCTYPE product SYSTEM "product.dtd">',
    omitNullAttributes: true,
    omitEmptyAttributes: true,
    spaceInEmptyElements: false,
    escapeAttributes: true
)) {
    product(id: '1', description: null, sku: '') {
        name('Widget')
        empty()
    }
}
```

When you need the same formatting control for other request helpers, render the XML first and then pass the
result to `httpPost`, `httpPut`, or `httpPatch` with `application/xml`:

```groovy
import org.apache.grails.testing.http.client.utils.XmlUtils

def payload = XmlUtils.toXml(
    omitDeclaration: false,
    prettyPrint: true,
    indent: '    ',
    lineSeparator: '\n',
    doctype: '<!DOCTYPE product SYSTEM "product.dtd">',
    omitNullAttributes: true,
    omitEmptyAttributes: true,
    spaceInEmptyElements: false,
    escapeAttributes: true
) {
    product(id: '1', description: null, sku: '') {
        name('Widget')
        empty()
    }
}

httpPut('/products/1', payload, 'application/xml')
```

Supported `XmlUtils.Format` options include:

- Declaration and document metadata: `omitDeclaration`, `xmlVersion`, `charset`, `doubleQuotes`, `doctype`
- Pretty-printing layout: `prettyPrint`, `indent`, `lineSeparator`
- Empty elements and attribute handling: `expandEmptyElements`, `spaceInEmptyElements`,
  `omitEmptyAttributes`, `omitNullAttributes`
- Attribute escaping: `escapeAttributes`

`XmlUtils.toXml(...)` also supports inline named options, so shorter cases can be written as:

```groovy
def payload = XmlUtils.toXml(omitNullAttributes: true, spaceInEmptyElements: false) {
    product(name: 'Widget', description: null) {
        empty()
    }
}

httpPost('/products', payload, 'application/xml')
```

