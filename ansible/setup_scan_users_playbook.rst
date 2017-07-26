Setup Scan Users Playbook
=========================
This playbook creates different test users that will scan target systems.

This playbook executes the `setup_scan_users` role which performs the following tasks:

- Creates users (scanadmin, scanbasic)
- Adds RHEL & RHEL Extras repositories
- Adds libselinux-python support
- Adds an authorized ssh key to users (root, scanadmin, scanbasic)
- Allows scanadmin to run all commands via sudo without a password


In order to properly run this playbook you must supply the following variables::

  yum_repo_file: Repo file name
  rhel7_yum_repo_name: RHEL7 repository name
  rhel7_yum_repo_url: RHEL7 repository url
  rhel7_yum_extras_repo_name: RHEL7 Extras repository name
  rhel7_yum_extras_repo_url: RHEL7 Extras repository url
  rhel6_yum_repo_name: RHEL 7 repository name
  rhel6_yum_repo_url: RHEL6 repository url
  rhel6_yum_extras_repo_name: RHEL6 Extras repository name
  rhel6_yum_extras_repo_url: RHEL6 Extras repopository url
  ssh_public_key_file: "{{ lookup('file', '<SSH KEYFILE LOCATION>) }}"


Target System Requirements
--------------------------

The target systems must have the following packages installed:

* python
* python-dnf
* python-pip

Example Usage
--------------------------
You can execute this playbook with the following command::

  ansible-playbook -i sonar-hosts --extra-vars="@sonar-extra-vars.yml" -k -u root -l <group> sonar-setup-user.yaml

Where ``sonar-hosts`` is an Ansible inventory file, ``sonar-extra-vars.yml`` is a YAML
file containing the appropriate values described above, ``group`` is a limit to a
group defined in the inventory file like ``vcenter``.
