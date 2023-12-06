import yaml
import json
import os

keys=['packageName', 'typeName', 'subtypes', 'methodName', 'signature', 'ext',
  'input', 'kind', 'provenance']
with open('config.json', 'r') as f:
    config = json.load(f)
rule_dir = config['codeql_rule_dir']
# rule_dir="/home/wzz/codeql/github-codeql/java/ql/lib/ext/"
rule_files=[rule_dir+file for file in os.listdir(rule_dir) if file.endswith('.yml')]
final_data=[]
# Load YAML file
for rule_file in rule_files:
    with open(rule_file, 'r') as file:
        data = yaml.safe_load(file)
    # Extract sinkModel data
    sink_model_origin_data = []
    for extension in data['extensions']:
        if extension['addsTo']['extensible'] == 'sinkModel':
            sink_model_origin_data.extend(extension['data'])

    sink_model_data = []
    for data in sink_model_origin_data:
        sink_model_data.append({keys[i]:data[i] for i in range(8)})
    final_data.extend(sink_model_data)
# Convert to JSON string
json_string = json.dumps(final_data)

# Print the JSON string
print(json_string)