<%=packageName ? "package ${packageName}" : ''%>

import grails.plugin.scaffolding.annotation.Scaffold<% if (extendsClass) { %>
import ${extendsClass}<% } %>

<% if (extendsClass) { %>@Scaffold(${extendsClassName}<${className}>)<% } else { %>@Scaffold(${className})<% } %>
class ${className}Service {
}
