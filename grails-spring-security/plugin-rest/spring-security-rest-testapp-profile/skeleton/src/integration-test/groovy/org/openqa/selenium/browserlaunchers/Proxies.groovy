package org.openqa.selenium.browserlaunchers

import org.openqa.selenium.Capabilities

class Proxies {
    static Proxy extractProxy(Capabilities capabilities) {
        return Proxy.extractFrom(capabilities)
    }
}