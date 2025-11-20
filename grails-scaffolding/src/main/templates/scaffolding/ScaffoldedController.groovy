<% if (namespace) { %><%=packageName ? "package ${packageName}.${namespace}" : "package ${namespace}"%>

import ${packageName}.${className}<% } else { %><%=packageName ? "package ${packageName}" : ''%><% } %>

import grails.plugin.scaffolding.annotation.Scaffold<% if (useService) { %>
import grails.plugin.scaffolding.RestfulServiceController<% } %>

<% if (useService) { %>@Scaffold(RestfulServiceController<${className}>)<% } else { %>@Scaffold(domain = ${className})<% } %>
class ${className}Controller {<% if (namespace) { %>
    static namespace = '${namespace}'
<% } %>}
