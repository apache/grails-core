<!DOCTYPE html>
<html>
<head>
    <title>Each Tag Test</title>
</head>
<body>
    <h1>Each Tag Test</h1>
    <ul id="item-list">
        <g:each in="${items}" var="item" status="i">
            <li class="item" data-index="${i}">${item}</li>
        </g:each>
    </ul>
    <p id="item-count">Total items: ${items.size()}</p>
</body>
</html>
