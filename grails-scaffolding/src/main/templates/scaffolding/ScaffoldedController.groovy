<% if (namespace) { %>
<%=packageName ? "package ${packageName}.${namespace}" : "package ${namespace}"%>
<% } else { %>
<%=packageName ? "package ${packageName}" : ''%>
<% } %>

import grails.artefact.controller.support.Scaffold

<% if (useService) { %>
@Scaffold(${className}Service<${className}>)
<% } else { %>
@Scaffold(domain = ${className})
<% } %>
class ${className}Controller {
<% if (namespace) { %>
    static namespace = '${namespace}'
<% } %>
}
