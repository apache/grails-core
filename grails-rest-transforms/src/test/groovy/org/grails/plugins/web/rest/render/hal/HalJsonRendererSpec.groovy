/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.web.rest.render.hal

import com.fasterxml.jackson.databind.ObjectMapper
import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.render.Renderer
import grails.rest.render.hal.HalJsonCollectionRenderer
import grails.rest.render.hal.HalJsonRenderer
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.util.GrailsWebUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import grails.web.mime.MimeType
import groovy.test.NotYetImplemented
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.AbstractPersistentEntity
import org.grails.datastore.mapping.model.AbstractPersistentProperty
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.MockHibernateProxyHandler
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.convert.converter.Converter
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.util.WebUtils
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.xml.bind.DatatypeConverter

/**
 */
class HalJsonRendererSpec extends Specification {

    @Shared
    ObjectMapper objectMapper

    void setupSpec() {

        objectMapper = new ObjectMapper()

        // ensure clean state
        ShutdownOperations.runOperations()
    }

    void cleanup() {
        ShutdownOperations.runOperations()
    }

    boolean jsonEquals(String json1, String json2) {
        def tree1 = objectMapper.readTree(json1)
        def tree2 = objectMapper.readTree(json2)
        tree1 == tree2
    }

    @Issue('GRAILS-10372')
    void "Test that the HAL renderer renders JSON values correctly for domains"() {
        given:"A HAL renderer"
            HalJsonRenderer renderer = getRenderer()
            renderer.prettyPrint = true

        when:"A domain object is rendered"
            def webRequest = boundMimeTypeRequest()
            (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
            def response = webRequest.response as MockHttpServletResponse
            def renderContext = new ServletRenderContext(webRequest)
            def product = new Product(name: "MacBook", numberInStock: 10, category: new Category(name: 'Laptops'))
            renderer.render(product, renderContext)

        then:"The resulting HAL is correct"
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "numberInStock": 10,
            "name": "MacBook",
            "_embedded": {
                "category": {
                    "_links": {
                        "self": {
                            "href": "http://localhost/category/index",
                            "hreflang": "en"
                        }
                    },
                    "name": "Laptops"
                }
            }
        }''')

    }

    @Issue('GRAILS-10499')
    void "Test that the HAL rendered renders JSON values correctly for collection" () {
        given: "A HAL Collection renderer"
            HalJsonCollectionRenderer renderer = getCollectionRenderer()
            renderer.prettyPrint = true

        when: "A collection of domian objects is rendered"
            def webRequest = boundMimeTypeRequest()
            (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
            def response = webRequest.response as MockHttpServletResponse
            def renderContext = new ServletRenderContext(webRequest)
            def products = [
                new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops')),
                new Product(name: "iMac", numberInStock: 42, category:  new Category(name: 'Desktops'))
            ]
            renderer.render(products, renderContext)

        then:"The resulting HAL is correct"
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name,
                    GrailsWebUtil.DEFAULT_ENCODING)
            jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/product/Macbook",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "_embedded": {
                "product": [
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/products",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "numberInStock": 10,
                        "name": "MacBook",
                        "_embedded": {
                            "category": {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/category/index",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "Laptops"
                            }
                        }
                    },
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/products",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "numberInStock": 42,
                        "name": "iMac",
                        "_embedded": {
                            "category": {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/category/index",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "Desktops"
                            }
                        }
                    }
                ]
            }
        }''')

    }
    
    @Issue('GRAILS-10533')
    void "Test customizing the embedded name for a rendered collection of domain objects" () {
        given: "A HAL Collection renderer with a custom embedded name"
            HalJsonCollectionRenderer renderer = getCollectionRenderer()
            renderer.prettyPrint = true
            renderer.collectionName = 'schtuff'

        when: "A collection of domian objects is rendered"
            def webRequest = boundMimeTypeRequest()
            (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
            def response = webRequest.response as MockHttpServletResponse
            def renderContext = new ServletRenderContext(webRequest)
            def products = [
                new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops')),
                new Product(name: "iMac", numberInStock: 42, category:  new Category(name: 'Desktops'))
            ]
            renderer.render(products, renderContext)

        then:"The resulting HAL is correct"
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name,
                    GrailsWebUtil.DEFAULT_ENCODING)
            jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/product/Macbook",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "_embedded": {
                "schtuff": [
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/products",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "numberInStock": 10,
                        "name": "MacBook",
                        "_embedded": {
                            "category": {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/category/index",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "Laptops"
                            }
                        }
                    },
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/products",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "numberInStock": 42,
                        "name": "iMac",
                        "_embedded": {
                            "category": {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/category/index",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "Desktops"
                            }
                        }
                    }
                ]
            }
        }''')

    }

    @Issue('GRAILS-10372')
    void "Test that the HAL renderer renders JSON values correctly for simple POGOs"() {
        given:"A HAL renderer"
            HalJsonRenderer renderer = getRenderer()
            renderer.prettyPrint = true

            when:"A domain object is rendered"
            def webRequest = boundMimeTypeRequest()
            (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
            def response = webRequest.response as MockHttpServletResponse
            def renderContext = new ServletRenderContext(webRequest)
            def product = new SimpleProduct(name: "MacBook", numberInStock: 10, category: new SimpleCategory(name: 'Laptops'))
            renderer.render(product, renderContext)

        then:"The resulting HAL is correct"
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/product/Macbook",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "category": {
                "name": "Laptops"
            },
            "name": "MacBook",
            "numberInStock": 10
        }''')

    }
    
    @Issue('GRAILS-10512')
    void "Test that the HAL renderer renders JSON values correctly for a collection of simple POGOs"() {
        given:"A HAL renderer"
            HalJsonRenderer renderer = getRenderer()
            renderer.prettyPrint = true
 
            when:"A collection of POGO is rendered"
            def webRequest = boundMimeTypeRequest()
            (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
            def response = webRequest.response as MockHttpServletResponse
            def renderContext = new ServletRenderContext(webRequest)
            def products = [
                new SimpleProduct(name: "MacBook", numberInStock: 10, category: new SimpleCategory(name: 'Laptops')),
                new SimpleProduct(name: "iMac", numberInStock: 8, category: new SimpleCategory(name: 'Desktops'))
            ]
            renderer.render(products, renderContext)
 
        then:"The resulting HAL is correct"
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/product/Macbook",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "_embedded": [
                {
                    "_links": {
                        "self": {
                            "href": "http://localhost/product/Macbook",
                            "hreflang": "en",
                            "type": "application/hal+json"
                        }
                    },
                    "category": {
                        "name": "Laptops"
                    },
                    "name": "MacBook",
                    "numberInStock": 10
                },
                {
                    "_links": {
                        "self": {
                            "href": "http://localhost/product/Macbook",
                            "hreflang": "en",
                            "type": "application/hal+json"
                        }
                    },
                    "category": {
                        "name": "Desktops"
                    },
                    "name": "iMac",
                    "numberInStock": 8
                }
            ]
        }''')
 
    }

    @Issue('GRAILS-10520')
    void "Test that HAL renders JSON correctly for eagerly loaded domain objects"(){
        given: "A HAL Renderer"
        def renderer = getEmployeeRenderer()
        and: "Eagerly loaded domain objects"
        def employee = new Employee(name:'employee1', projects: [new Project(name: 'project1')])

        when: "I render eagerly loaded domain object"
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        def renderContext = new ServletRenderContext(webRequest)
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/employees/employee1")
        renderer.render(employee,renderContext)
        def response = webRequest.response as MockHttpServletResponse

        then: "The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name,
                GrailsWebUtil.DEFAULT_ENCODING)
        /*
            TODO: Fix the link generation in Unit Tests.
            Links are not rendered correctly. This seems to be an existing bug in link generation and all other
            tests have same problem. For now, the assertion below is using manipulated links for expected value.
         */
        jsonEquals(response.contentAsString, '{"_links":{"self":{"href":"http://localhost/employees","hreflang":"en","type":"application/hal+json"}},"name":"employee1","_embedded":{"projects":[{"_links":{"self":{"href":"http://localhost/project/index","hreflang":"en"}},"name":"project1","_embedded":{"employees":[]}}]}}')


    }

    @Issue('GRAILS-10499')
    @NotYetImplemented
    void "Test that the HAL rendered renders JSON values correctly for collections with repeated elements" () {
        given: "A HAL Collection renderer"
        HalJsonCollectionRenderer renderer = getCollectionRenderer()
        renderer.prettyPrint = true

        when: "A collection of domian objects is rendered"
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
        def response = webRequest.response as MockHttpServletRequest
        def renderContext = new ServletRenderContext(webRequest)
        def products = [
            new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops')),
            new Product(name: "iMac", numberInStock: 42, category:  new Category(name: 'Desktops')),
            new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops'))
        ]
        renderer.render(products, renderContext)

        then:"The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        jsonEquals(response.contentAsString, '''{
          "_links": {
            "self": {
              "href": "http://localhost/product/Macbook",
              "hreflang": "en",
              "type": "application/hal+json"
            }
          },
          "_embedded": {
            "product": [
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "MacBook",
                "numberInStock": 10,
                "_embedded": {
                  "category": {
                    "_links": {
                      "self": {
                        "href": "http://localhost/category/index",
                        "hreflang": "en"
                      }
                    },
                    "name": "Laptops"
                  }
                }
              },
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "iMac",
                "numberInStock": 42,
                "_embedded": {
                  "category": {
                    "_links": {
                      "self": {
                        "href": "http://localhost/category/index",
                        "hreflang": "en"
                      }
                    },
                    "name": "Desktops"
                  }
                }
              },
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "MacBook",
                "numberInStock": 10,
                "_embedded": {
                  "category": {
                    "_links": {
                      "self": {
                        "href": "http://localhost/category/index",
                        "hreflang": "en"
                      }
                    },
                    "name": "Laptops"
                  }
                }
              }
            ]
          }
        }''')

    }

    @Issue('GRAILS-10499')
    @NotYetImplemented
    void "Test that the HAL rendered renders JSON values correctly for collections with elided elements" () {
        given: "A HAL Collection renderer"
        HalJsonCollectionRenderer renderer = getCollectionRenderer()
        renderer.prettyPrint = true

        when: "A collection of domian objects is rendered"
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
        def response = webRequest.response as MockHttpServletResponse
        def renderContext = new ServletRenderContext(webRequest)
        def products = [
            new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops')),
            new Product(name: "iMac", numberInStock: 42, category:  new Category(name: 'Desktops')),
            new Product(name: "MacBook", numberInStock: 10, category:  new Category(name: 'Laptops'))
        ]
        renderer.render(products, renderContext)

        then:"The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name,
            GrailsWebUtil.DEFAULT_ENCODING)
        objectMapper.readTree(response.contentAsString) == objectMapper.readTree('''{
          "_links": {
            "self": {
              "href": "http://localhost/product/Macbook",
              "hreflang": "en",
              "type": "application/hal+json"
            }
          },
          "_embedded": {
            "product": [
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "MacBook",
                "numberInStock": 10,
                "_embedded": {
                  "category": {
                    "_links": {
                      "self": {
                        "href": "http://localhost/category/index",
                        "hreflang": "en"
                      }
                    },
                    "name": "Laptops"
                  }
                }
              },
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "iMac",
                "numberInStock": 42,
                "_embedded": {
                  "category": {
                    "_links": {
                      "self": {
                        "href": "http://localhost/category/index",
                        "hreflang": "en"
                      }
                    },
                    "name": "Desktops"
                  }
                }
              },
              {
                "_links": {
                  "self": {
                    "href": "http://localhost/products",
                    "hreflang": "en",
                    "type": "application/hal+json"
                  }
                },
                "name": "MacBook",
                "numberInStock": 10
              }
            ]
          }
        }''')

    }

    @Issue('GRAILS-10781')
    void 'Test that the HAL renderer renders enums successfully for non domain classes'() {
        given: 'A HAL render'
        Renderer render = new HalJsonRenderer(Moment)

        when: 'A non domain is rendered'
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/moment/theFuture")
        def response = webRequest.response as MockHttpServletResponse
        def renderContext = new ServletRenderContext(webRequest)
        def moment = new Moment(type: Moment.Category.FUTURE)
        renderer.render moment, renderContext

        then: 'The resulting HAL is correct'
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        jsonEquals(response.contentAsString, '{"_links":{"self":{"href":"http://localhost/moment/theFuture","hreflang":"en","type":"application/hal+json"}},"type":"FUTURE"}')
    }

    @Issue('GRAILS-10372 GRAILS-10781')
    void "Test that the HAL renderer renders mixed fields (dates, enums) successfully for domains"() {
        given:"A HAL renderer"
        HalJsonRenderer renderer = getEventRenderer()
        renderer.prettyPrint = true

        when:"A domain object is rendered"
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/event/Lollapalooza")
        def response = webRequest.response as MockHttpServletResponse
        def renderContext = new ServletRenderContext(webRequest)
        def cal = Calendar.instance
        cal.with {
            clear()
            set MONTH, NOVEMBER
            set YEAR, 2013
            set DATE, 8
            set HOUR_OF_DAY, 16
            set MINUTE, 12
            set SECOND, 30
            setTimeZone TimeZone.getTimeZone('GMT-5:00')
        }
        def event = new Event(name: "Lollapalooza", date: cal.time, state: Event.State.OPEN)

        renderer.render(event, renderContext)

        then:"The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/events",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "date": "2013-11-08T21:12:30+0000",
            "name": "Lollapalooza",
            "state": "OPEN"
        }''')
    }

    @Issue('https://github.com/apache/grails-core/issues/5671')
    @Ignore // fails in github vs locally due to time zone issues
    void "Test that the HAL renderer allows for different date converters"() {
        given:"A HAL renderer"
        HalJsonRenderer renderer = getEventRenderer()
        renderer.prettyPrint = true
        renderer.dateToStringConverter = new Converter<Date, String>() {
            @Override
            String convert(Date source) {
                final GregorianCalendar cal = new GregorianCalendar()
                cal.setTime(source)
                cal.setTimeZone(TimeZone.getTimeZone('America/Los_Angeles'))
                DatatypeConverter.printDateTime(cal)
            }
        }

        when:"A domain object is rendered"
        def webRequest = boundMimeTypeRequest()
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/event/Lollapalooza")
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        def response = webRequest.response as MockHttpServletResponse
        def renderContext = new ServletRenderContext(webRequest)
        Calendar calendar = Calendar.getInstance()
        calendar.set(2013, 10, 8, 13, 12, 30)
        def event = new Event(name: "Lollapalooza", date: calendar.getTime(), state: Event.State.OPEN)

        renderer.render(event, renderContext)

        then:"The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        jsonEquals(response.contentAsString, '''{
          "_links": {
            "self": {
              "href": "http://localhost/events",
              "hreflang": "en",
              "type": "application/hal+json"
            }
          },
          "date": "2013-11-08T18:12:30+0000",
          "name": "Lollapalooza",
          "state": "OPEN"
        }''')

    }


    @Issue('GRAILS-11100')
    void "Test that the HAL renderer ignores null values for embedded single ended domain objects" () {
        given:"A HAL renderer"
        HalJsonRenderer renderer = getRenderer()
        renderer.prettyPrint = false

        when:"A domain object is rendered"
        def webRequest = boundMimeTypeRequest()
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/product/Macbook")
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        def response = webRequest.response as MockHttpServletResponse
        def renderContext = new ServletRenderContext(webRequest)
        def product = new Product(name: "MacBook", numberInStock: 10, category: null)
        renderer.render(product, renderContext)

        then:"The resulting HAL is correct"
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        jsonEquals(response.contentAsString, '{"_links":{"self":{"href":"http://localhost/products","hreflang":"en","type":"application/hal+json"}},"numberInStock":10,"name":"MacBook","_embedded":{}}')
    }

    @Issue('https://github.com/apache/grails-core/issues/10293')
    void "Test that the HAL renderer renders JSON values correctly for collections with a many-to-one association" () {
        given:
        HalJsonCollectionRenderer renderer = getMemberCollectionRenderer()
        renderer.proxyHandler = new MockHibernateProxyHandler()

        and:
        def team = new Team(name: 'Test Team')
        def members = [
            new Member(name: "One", team: team),
            new Member(name: "Two", team: team)
        ]

        and:
        def webRequest = configureMembersWebRequest()
        def renderContext = new ServletRenderContext(webRequest)
        def response = webRequest.response as MockHttpServletResponse

        when:
        renderer.render(members, renderContext)

        then:
        response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)

        and:
        jsonEquals(response.contentAsString, '''{
            "_links": {
                "self": {
                    "href": "http://localhost/members/",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "_embedded": {
                "member": [
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/members",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            },
                            "team": {
                                "href": "http://localhost/teams",
                                "hreflang": "en"
                            }
                        },
                        "name": "One"
                    },
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost/members",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            },
                            "team": {
                                "href": "http://localhost/teams",
                                "hreflang": "en"
                            }
                        },
                        "name": "Two"
                    }
                ]
            }
        }''')
    }

    protected configureMembersWebRequest() {
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader("ACCEPT", "application/hal+json")
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/members/")
        webRequest
    }

    protected HalJsonCollectionRenderer getCollectionRenderer() {
        def renderer = new HalJsonCollectionRenderer(Product)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/products"(resources: "product")
        }
        renderer
    }

    protected HalJsonCollectionRenderer getMemberCollectionRenderer() {
        def renderer = new HalJsonCollectionRenderer(Member)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/members"(resources: "member")
            "/teams"(resources: "team")
        }
        renderer.prettyPrint = true
        renderer
    }

    protected HalJsonRenderer getRenderer() {
        def renderer = new HalJsonRenderer(Product)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/products"(resources: "product")
        }
        renderer
    }

    protected HalJsonRenderer getEmployeeRenderer() {
        def renderer = new HalJsonRenderer(Employee)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/employees"(resources: "employee")
        }
        renderer.prettyPrint = false
        renderer
    }

    protected HalJsonRenderer getEventRenderer() {
        def renderer = new HalJsonRenderer(Event)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/events"(resources: "event")
        }
        renderer
    }

    protected HalJsonRenderer getSpecialEventRenderer() {
        def renderer = new HalJsonRenderer(SpecialEvent)
        renderer.mappingContext = mappingContextForSpecialEvent
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            "/specialEvents"(resources: "specialEvent")
        }
        renderer
    }

    MappingContext getMappingContextForSpecialEvent() {
        final context = new KeyValueMappingContext("")
        def specialEventEntity = context.addPersistentEntity(SpecialEvent)
        // To make 'specialType' appear as a persistentProperty on SpecialEvent we need to fake it here
        def pp = new AbstractPersistentProperty(specialEventEntity, context, 'specialType', SpecialType) {
            PropertyMapping getMapping() {
                return null
            }
        }
        specialEventEntity.persistentProperties.add(pp)
        specialEventEntity.persistentPropertyNames.add('specialType')
        (specialEventEntity as AbstractPersistentEntity).propertiesByName.put('specialType', pp)
        context
    }

    MappingContext getMappingContext() {
        final context = new KeyValueMappingContext("")
        context.addPersistentEntity(Product)
        context.addPersistentEntity(Category)
        context.addPersistentEntity(Event)
        context.addPersistentEntity(Employee)
        context.addPersistentEntity(Project)
        context.addPersistentEntities(Team)
        context.addPersistentEntities(Member)
        return context
    }
    LinkGenerator getLinkGenerator(Closure mappings) {
        def generator = new DefaultLinkGenerator("http://localhost", null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = getUrlMappingsHolder mappings
        return generator;
    }
    UrlMappingsHolder getUrlMappingsHolder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def allMappings = evaluator.evaluateMappings mappings
        return new DefaultUrlMappingsHolder(allMappings)
    }

    private GrailsWebRequest boundMimeTypeRequest() {
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        def application = new DefaultGrailsApplication()
        application.config = testConfig
        ctx.beanFactory.registerSingleton("mimeUtility", new DefaultMimeUtility(buildMimeTypes(application)))

        ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        ctx.refresh()
        GrailsWebMockUtil.bindMockWebRequest(ctx)
    }

    String applicationConfigText = '''
grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = true
grails.mime.types = [
                      all: '*/*',
                      html: ['text/html','application/xhtml+xml'],
                      xml: ['text/xml', 'application/xml'],
                      text: 'text/plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      csv: 'text/csv',
                      json: ['application/json','text/json'],
                      form: 'application/x-www-form-urlencoded',
                      hal:           ['application/hal+json','application/hal+xml'],
                      multipartForm: 'multipart/form-data'
                    ]
'''

    private Config getTestConfig() {
        def s = new ConfigSlurper()
        def config = s.parse(String.valueOf(applicationConfigText))

        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource("grails", config))


        return new PropertySourcesConfig(propertySources)
    }

    private MimeType[] buildMimeTypes(application) {
        final def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)

        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        final ApplicationContext context = bb.createApplicationContext()
        final MimeTypesConfiguration mimeTypesConfiguration = context.getBean(MimeTypesConfiguration)
        mimeTypesConfiguration.mimeTypes()
    }
}

@Entity
class Product {

    String name
    Integer numberInStock
    Category category

    @SuppressWarnings('unused')
    static embedded = ['category']
}

@Entity
class Category {

    String name

    @Override
    String toString() {
        name
    }

    /*
     * We need these defined for when we're checking if objects are actually written (since we're checking our
     * set 'writtenObjects' if something's there already)
     */
    @Override
    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        Category category = (Category) o

        if (name != category.name) {
            return false
        }

        return true
    }

    @Override
    int hashCode() {
        return name.hashCode()
    }
}

@Entity
class Event {

    String name
    Date date
    State state

    enum State { OPEN, CLOSED }
}

@Entity
class SpecialEvent {
    String name
    SpecialType specialType
}

@Entity
class Project {

    String name

    @SuppressWarnings('unused')
    static hasMany = [employees: Employee]

    @SuppressWarnings('unused')
    static mapping = {
        employees lazy: false
    }
}
@Entity
class Employee {

    String name

    @SuppressWarnings('unused')
    static hasMany = [projects: Project]

    @SuppressWarnings('unused')
    static belongsTo = Project

    @SuppressWarnings('unused')
    static mapping = {
        projects lazy: false
    }
}

@Entity
class Team {

    String name

    @SuppressWarnings('unused')
    static hasMany = [members: Member]
}

@Entity
class Member {

    String name

    @SuppressWarnings('unused')
    static belongsTo = [team: Team]
}

class Moment {
    Category type
    enum Category { PAST, PRESENT, FUTURE }
}

class SimpleProduct {
    String name
    Integer numberInStock
    SimpleCategory category
}

class SimpleCategory {
    String name
}

class SpecialType {
}