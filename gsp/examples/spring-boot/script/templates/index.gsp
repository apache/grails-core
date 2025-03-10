<%@ defaultCodec="HTML" %>
<html>
	<head>
		<title>GSP example</title>
		<meta name="keywords" content="gsp,example">
	</head>
	<body>
		<h3>Accessing the model</h3>
		Hello, ${name}.
		<h3>Iteration with a scriptlet</h3>
		<% [1,2,3,4].each { num -> %>
			<p><%="#${num}" %></p>
		<%}%>
		<h3>Iteration with the &lt;each&gt; tag</h3>
		<g:each in="${[1,2,3]}" var="num">
			<p>Number ${num}</p>
		</g:each>
		<h3>Using a tag library</h3>
		<g:dateFormat format="dd-MM-yyyy" date="${new Date()}" />

		<h3>Using a tag library in an expression</h3>
		<p>${g.dateFormat(format:'dd-MM-yyyy', date:new Date())}</p>

		<h3>Rendering a template</h3>
		<g:render template="subtemplate" />

		<h3>Rendering a template with a body</h3>
		<g:render template="template_body">		
			My template body
		</g:render>

		<h3>Applying a layout</h3>		
		<g:applyLayout name="sample">
		Text to decorate. 
		</g:applyLayout>

		<h3>Automatic XSS prevention</h3>		
		${'<script>test escaping</script>'}

		<h3>grailsApplication${grailsApplication == null?' not':''} set.</h3>
		<table>
		<g:each var="k,v" in="${grailsApplication.config}">
			<tr><td>${k}</td><td>${v}</td></tr>
		</g:each>
		<table>
	</body>
</html>