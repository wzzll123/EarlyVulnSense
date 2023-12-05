import json
import sys
json_result = []
def parse_sarif_file(file_path):
    with open(file_path, 'r') as f:
        sarif = json.load(f)

    for run in sarif['runs']:
        for invocation in run['invocations']:
            for toolExecutionNotification in invocation["toolExecutionNotifications"]:
                if toolExecutionNotification['descriptor']['id'] == 'java/utils/modelgenerator/sink-models':
                    json_result.append(toolExecutionNotification)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python get_sink_model.py <path/to/sarif/file>")
        sys.exit(1)

    parse_sarif_file(sys.argv[1])
    json_object = json.dumps(json_result)
    print(json_object)