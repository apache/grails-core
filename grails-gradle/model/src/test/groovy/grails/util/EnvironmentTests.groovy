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
package grails.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class EnvironmentTests {

    @BeforeEach
    protected void setUp() {
        Environment.reset()
        Metadata.reset()
    }

    @AfterEach
    protected void tearDown() {
        System.clearProperty(Environment.KEY)
        System.clearProperty(Environment.RELOAD_ENABLED)
        System.clearProperty(Environment.RELOAD_LOCATION)

        Metadata.reset()
        Environment.reset()
    }

    @Test
    void testExecuteForEnvironment() {

        System.setProperty("grails.env", "prod")
        assertEquals Environment.PRODUCTION, Environment.getCurrent()

        assertEquals "prod", Environment.executeForCurrentEnvironment {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        assertEquals "dev", Environment.executeForEnvironment(Environment.DEVELOPMENT) {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        System.setProperty("grails.env", "dev")
        assertEquals Environment.DEVELOPMENT, Environment.getCurrent()

        assertEquals "dev", Environment.executeForCurrentEnvironment {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        System.setProperty("grails.env", "soe")
        assertEquals Environment.CUSTOM, Environment.getCurrent()

        assertEquals "some other environment", Environment.executeForCurrentEnvironment {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }
    }

    @Test
    void testGetEnvironmentSpecificBlock() {

        System.setProperty("grails.env", "prod")
        assertEquals Environment.PRODUCTION, Environment.getCurrent()

        def callable = Environment.getEnvironmentSpecificBlock {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        assertEquals "prod", callable.call()

        System.setProperty("grails.env", "dev")
        assertEquals Environment.DEVELOPMENT, Environment.getCurrent()

        callable = Environment.getEnvironmentSpecificBlock {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        assertEquals "dev", callable.call()

        System.setProperty("grails.env", "soe")
        assertEquals Environment.CUSTOM, Environment.getCurrent()

        callable = Environment.getEnvironmentSpecificBlock {
            environments {
                production {
                    "prod"
                }
                development {
                    "dev"
                }
                soe {
                    "some other environment"
                }
            }
        }

        assertEquals "some other environment", callable.call()
    }

    @Test
    void testGetCurrent() {

        System.setProperty("grails.env", "prod")
        assertEquals Environment.PRODUCTION, Environment.getCurrent()

        System.setProperty("grails.env", "dev")
        assertEquals Environment.DEVELOPMENT, Environment.getCurrent()

        System.setProperty("grails.env", "soe")
        assertEquals Environment.CUSTOM, Environment.getCurrent()
    }

    @Test
    void testGetEnvironment() {
        assertEquals Environment.DEVELOPMENT, Environment.getEnvironment("dev")
        assertEquals Environment.TEST, Environment.getEnvironment("test")
        assertEquals Environment.PRODUCTION, Environment.getEnvironment("prod")
        assertNull Environment.getEnvironment("doesntexist")
    }

    @Test
    void testSystemPropertyOverridesMetadata() {
        Metadata.getInstance(new ByteArrayInputStream('''
grails:
    env: production
'''.bytes))

        assertEquals Environment.PRODUCTION, Environment.getCurrent()

        System.setProperty(Environment.KEY, "dev")
        assertEquals Environment.DEVELOPMENT, Environment.getCurrent()

        System.clearProperty(Environment.KEY)
        assertEquals Environment.PRODUCTION, Environment.getCurrent()

        Metadata.getInstance(new ByteArrayInputStream(''.bytes))
        Environment.reset()
        assertEquals Environment.DEVELOPMENT, Environment.getCurrent()
    }

    @Test
    void testReloadEnabled() {
        Metadata.getInstance(new ByteArrayInputStream('''
grails:
    env: production
'''.bytes))

        assertFalse Environment.getCurrent().isReloadEnabled(), "reload should be disabled by default in production"

        System.setProperty("grails.env", "dev")
        assertFalse Environment.getCurrent().isReloadEnabled(), "reload should be disabled by default in development unless base.dir set"

        System.setProperty("base.dir", ".")
        assertTrue Environment.getCurrent().isReloadEnabled(), "reload should be enabled by default in development if base.dir set"

        System.clearProperty("base.dir")
        System.setProperty("grails.env", "prod")
        assertFalse Environment.getCurrent().isReloadEnabled(), "reload should be disabled by default in production if base.dir set"

        System.setProperty(Environment.RELOAD_ENABLED, "true")
        assertFalse Environment.getCurrent().isReloadEnabled(), "reload should be disabled by default in production if reload enabled set but not location"

        System.setProperty(Environment.RELOAD_LOCATION, ".")
        assertTrue Environment.getCurrent().isReloadEnabled(), "reload should be enabled by default in production if reload enabled and location set"
    }
}
