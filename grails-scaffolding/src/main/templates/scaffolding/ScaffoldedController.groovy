<% if (namespace) { %><%=packageName ? "package ${packageName}.${namespace}" : "package ${namespace}"%>

import ${packageName}.${className}<% } else { %><%=packageName ? "package ${packageName}" : ''%><% } %>

import grails.plugin.scaffolding.annotation.Scaffold<% if (extendsClass) { %>
import ${extendsClass}<% } else if (useService) { %>
import grails.plugin.scaffolding.RestfulServiceController<% } %>

<% if (extendsClass) { %>@Scaffold(${extendsClassName}<${className}>)<% } else if (useService) { %>@Scaffold(RestfulServiceController<${className}>)<% } else { %>@Scaffold(${className})<% } %>
class ${className}Controller {<% if (namespace) { %>
    static namespace = '${namespace}'
<% } %>}
