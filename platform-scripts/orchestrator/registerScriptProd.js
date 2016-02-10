var fs = require('fs');
var readLine = require('readline');
var Client = require('node-rest-client').Client;
var client = new Client();
var host = "http://52.77.241.169:8080/taxonomy-service";

console.log('');
var scriptAsString = fs.readFileSync(process.argv[3]);
var rl = readLine.createInterface({
	input: fs.createReadStream(process.argv[2])
});

var lines = [];
rl.on('line', function (line) {
	var trimmedLine = line.trim();
	if(trimmedLine.length > 0) {
		lines.push(trimmedLine);
	}
});

rl.on('close', function() {
	var command = lines.join('\n');
	var script = JSON.parse(scriptAsString);
	script.body = command;
	var args = {
        headers: {
            "Content-Type": "application/json",
            "user-id": "analytics"
        },
        data: script
    };
    if(process.argv[4] == 'register') {
    	client.post(host + "/v1/orchestrator/register/script", args, function (data, response) {
			console.log('######## Register script - Response ########');
			console.log(data);
			console.log('############################################');
			console.log('');
			loadCommands();
		});
    } else {
    	client.patch(host + "/v1/orchestrator/update/script/" + script.name, args, function (data, response) {
			console.log('########  Update script - Response  ########');
			console.log(data);
			console.log('############################################');
			console.log('');
			loadCommands();
		});
    }
})

function loadCommands() {
	var args = {
        headers: {
            "Content-Type": "application/json",
            "user-id": "analytics"
        }
    };
	client.get(host + "/v1/orchestrator/load/commands", args, function (data, response) {
		console.log('########  Load Commands - Response  ########');
		console.log(data);
		console.log('############################################');
		console.log('');
	});
}
