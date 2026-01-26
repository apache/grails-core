<!DOCTYPE html>
<html>
<head>
    <title>Content Negotiation Demo</title>
</head>
<body>
    <h1>Content Negotiation</h1>
    <p>Message: ${data.message}</p>
    <p>Timestamp: ${data.timestamp}</p>
    <ul>
        <g:each in="${data.items}" var="item">
            <li>${item}</li>
        </g:each>
    </ul>
</body>
</html>
