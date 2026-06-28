import os
import re
import sys
import subprocess

def main():
    if len(sys.argv) < 2:
        print("Usage: python fix_kubeconfig.py <kubeconfig_path>")
        sys.exit(1)
        
    kubeconfig_path = sys.argv[1]
    
    # 1. Get host workspace path from docker inspect
    try:
        host_ws = subprocess.check_output([
            'docker', 'inspect', 'cicd-jenkins', 
            '--format', '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}'
        ]).decode().strip()
        print("Host workspace:", host_ws)
        
        if '\\' in host_ws:
            parts = host_ws.split('\\')
            host_kube = parts[0] + '/' + parts[1] + '/' + parts[2] + '/.kube'
        else:
            parts = host_ws.split('/')
            host_kube = '/' + parts[1] + '/' + parts[2] + '/.kube'
    except Exception as e:
        print("Could not detect host path, using default:", e)
        host_kube = 'c:/Users/ZBOOK/.kube'
        
    print("Mounting host .kube directory from:", host_kube)
    
    # 2. Run docker container to fetch the host config
    try:
        host_config = subprocess.check_output([
            'docker', 'run', '--rm', 
            '-v', f'{host_kube}:/kube', 
            'alpine:latest', 'cat', '/kube/config'
        ]).decode()
    except Exception as e:
        print("Error reading host kubeconfig via docker:", e)
        sys.exit(1)
        
    # 3. Extract the port of the host's active server URL
    m = re.search(r'127.0.0.1:([0-9]+)', host_config)
    if not m:
        print("Could not find dynamic port in host config.")
        sys.exit(0)
        
    port = m.group(1)
    print("Detected dynamic Kubernetes port from host:", port)
    
    # 4. Update the target kubeconfig file in Jenkins
    if not os.path.exists(kubeconfig_path):
        print(f"Target kubeconfig file not found at: {kubeconfig_path}")
        sys.exit(1)
        
    with open(kubeconfig_path, 'r') as f:
        kb = f.read()
        
    # Replace server URL and remove certificate authority
    kb = re.sub(r'server:.*', f'server: https://host.docker.internal:{port}\n    insecure-skip-tls-verify: true', kb)
    kb = re.sub(r'certificate-authority-data:.*', '', kb)
    
    with open(kubeconfig_path, 'w') as f:
        f.write(kb)
        
    print(f"Successfully updated kubeconfig at: {kubeconfig_path}")

if __name__ == '__main__':
    main()
