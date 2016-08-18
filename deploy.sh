lein clean
lein cljsbuild once prod
lein uberjar
ansible-playbook -i infrastructure/ansible/prod infrastructure/ansible/deploy.yml
