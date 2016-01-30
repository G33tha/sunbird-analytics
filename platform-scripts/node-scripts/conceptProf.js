var cassandra = require('cassandra-driver');
var fs = require('fs');
var client = new cassandra.Client({ contactPoints: ['127.0.0.1'], keyspace: 'learner_db'});
var query = "SELECT * FROM learnerproficiency WHERE learner_id = 'c16820ca-195c-48ab-a9b0-1138f7a9cf20'";

client.execute(query, [], function(err, result) {
  	var learnerProf = result.first();
  	var prof = learnerProf.proficiency;
  	var modelParams = learnerProf.model_params;
  	var precisions = {};
  	for (k in modelParams) {
  		var mpString = modelParams[k];
  		var mp = JSON.parse(mpString);

  		var alpha = mp.alpha;
  		var beta = mp.beta;
  		var precision =  Math.log(Math.pow((alpha + beta), 2) * (alpha + beta + 1)/(alpha * beta));
  		precisions[k] = Math.round(precision * 100) / 100;
  	}
  	var graphString = fs.readFileSync('/Users/Santhosh/Downloads/numeracy.json');
  	var graph = JSON.parse(graphString);
  	var nodes = graph.result.subgraph.nodes;
  	nodes.forEach(function(node) {
  		if(prof[node.identifier]) {
  			node.proficiency = prof[node.identifier];
  			node.precision = precisions[node.identifier]
  		}
  	});
  	fs.writeFileSync('/Users/Santhosh/Downloads/numeracy_prof.json', JSON.stringify(graph, 2));
  	client.shutdown();
});