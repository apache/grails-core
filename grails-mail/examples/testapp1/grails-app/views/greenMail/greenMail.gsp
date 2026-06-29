<html>
<head>
    <title>Greenmail Inbox</title>
    <style>
        table {
            border-collapse: collapse;
        }
        th, td {
            border: 1px solid black;
            padding: 8px;
            text-align: left;
        }
        th {
            background-color: #f2f2f2;
        }
    </style>
</head>
<body>
<h1>📬 Greenmail Inbox</h1>

<table>
    <tr>
        <th>From</th>
        <th>To</th>
        <th>Subject</th>
        <th>Body</th>
    </tr>
    <g:each var="msg" in="${messages}">
        <tr>
            <td>${msg.from}</td>
            <td>${msg.to}</td>
            <td>${msg.subject}</td>
            <td>${msg.body}</td>
        </tr>
    </g:each>
</table>

</body>
</html>
