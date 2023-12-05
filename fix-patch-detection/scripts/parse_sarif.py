import json
import sys

json_result = []

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

def print_locations(rule_id,locations):
    result = f"{rule_id}, "
    for i,location in enumerate(locations):
        if i not in [0, len(locations)-1]:
            continue
        location_str = get_location_str(location)
        result = result + location_str +', '
    result = result[:-2]
    print(result)
def parse_sarif_file(file_path):
    with open(file_path, 'r') as f:
        sarif = json.load(f)

    for run in sarif['runs']:
        for result in run['results']:
            rule_id = result['ruleId']
            
            if 'codeFlows' not in result:
                pass
            else: 
                
                for code_flow in result.get('codeFlows'):
                    
                    for thread_flow in code_flow['threadFlows']:
                        tmp = {}
                        tmp['rule_id'] = rule_id
                        # tmp['type'] = 'taint'
                        tmp['code_flow'] = []
                        for i,location in enumerate(thread_flow['locations']):
                            # if i==0:
                            #     tmp['src_location'] = get_location_str(location['location'])
                            # elif i==len(thread_flow['locations']) -1:
                            #     tmp['target_location'] =get_location_str(location['location'])
                            # else:
                            tmp['code_flow'].append(get_location_str(location['location']))
                        # if tmp not in json_result and 'test' not in tmp['target_location']:
                        json_result.append(tmp)
if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python sarif_parser.py <path/to/sarif/file>")
        sys.exit(1)

    parse_sarif_file(sys.argv[1])
    json_object = json.dumps(json_result)
    print(json_object)