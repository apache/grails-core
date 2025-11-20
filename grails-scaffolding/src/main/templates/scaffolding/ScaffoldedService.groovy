<%=packageName ? "package ${packageName}" : ''%>

import grails.plugin.scaffolding.annotation.Scaffold

@Scaffold(domain = ${className})
class ${className}Service {
}
