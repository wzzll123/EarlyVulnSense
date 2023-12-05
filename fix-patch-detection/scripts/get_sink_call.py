import json
import sys
json_result = []
def parse_sarif_file(file_path):
    with open(file_path, 'r') as f:
        sarif = json.load(f)
    for run in sarif['runs']:
        for result in run['results']:
            tmp={}
            tmp['message']=result['message'] 
            for location in result['locations']:
                # print(location)
                tmp['location']=get_location_str(location)
            json_result.append(tmp)
def get_location_str(location):
    physical_location = location.get('physicalLocation')
    if physical_location:
        artifact_location = physical_location.get('artifactLocation')
        file_name = artifact_location['uri'].replace('/','.')
        region = physical_location.get('region')
        if region:
            start_line = region.get('startLine')
            start_column = region.get('startColumn')
            end_column = region.get('endColumn')
            return f"{file_name}:{start_line}:{start_column}:{end_column}"              

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python get_sink_call.py <path/to/sarif/file>")
        sys.exit(1)

    parse_sarif_file(sys.argv[1])
    json_object = json.dumps(json_result)
    print(json_object)