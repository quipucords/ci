nodepool playbook
=================

This playbook installs and configure `nodepool`_ on a system (currently tested
on a Fedora 25 target).

In order to nodepool properly work there are some required steps that need to
be run outside the playbook. First a SSH key pair should be created::

    ssh-keygen -f id_sonar_jenkins_rsa -C jenkins@sonar-jenkins.domain.com

Then create a new credentials on Jenkins go to Credentials → System → Global
credentials → Add Credentials and fill the following information:

* Kind: SSH Username with private key
* Scope: Global
* Username: jenkins
* Private key: mark Enter directly and paste the contents of the generated
  private key on the Key textarea.
* Passphrase: fill with the SSH passphrase or leave it blank if no passphrase
  is needed for the generated SSH key.
* ID: add some ID or leave it blank to make Jenkins generate one.
Description: any description that will help identify the credential.

After creating the credential, take note of the ID to fill later on the
nodepool configuration.

Next Jenkins must be configured to publish ZMQ events for all jobs. To do that
go to Manage Jenkins → Configure System. On ZMQ Event Publisher section, make
sure that Enable on all Jobs is checked. Take note of the TCP port to publish
on value (should be 8888).

Target System Requirements
--------------------------

The target systems must have the following packages installed:

* libselinux-python
* python
* python-dnf
* python-pip

For example, on Fedora 25 you can install those packages by running the
following command::

    dnf -y install @ansible-node libselinux-python

.. note::

    If you Jenkins is published over HTTPS make sure its Certificate Authority
    is trusted in case you used a custom SSL Certificate.

After running the playbook make sure to copy the SSH keypair to the
/var/lib/nodepool/.ssh directory. Make sure that the keypair and the .ssh
directory is with the proper permissions, on the target system::

    mkdir /var/lib/nodepool/.ssh
    # copy the keypair, better to rename from id_sonar_jenkins_rsa* to id_rsa*
    chmod 600 /var/lib/nodepool/.ssh/id_rsa
    chmod 644 /var/lib/nodepool/.ssh/id_rsa.pub

Providing the Expected Variables
--------------------------------

To ease the process of passing the expected variables, is recommended to create
a variables file. Here is an example file::

    # 8888 is the port where ZMQ is publishing the events
    nodepool_zmq_publishers:
      - tcp://sonar-jenkins.domain.com:8888

    nodepool_diskimages:
      - name: f25-np
        elements:
        - fedora-minimal
        - vm
        - simple-init
        - growroot
        - jenkins-slave
        release: 25
        env-vars:
          DIB_CHECKSUM: '1'
          DIB_GRUB_TIMEOUT: '0'
          DIB_IMAGE_CACHE: /opt/dib/cache
          TMPDIR: /opt/dib/tmp

    nodepool_labels:
      - name: f25-np
        image: f25-np
        min-ready: 0
        providers:
        - name: sonar-jenkins

    nodepool_providers:
      - name: sonar-jenkins
        cloud: sonar-jenkins
        max-servers: 10
        boot-timeout: 240
        launch-timeout: 900
        pool: "10.8.180.0/22"
        networks:
        - name: sonar-jenkins
        clean-floating-ips: true
        images:
        - name: f25-np
          key-name: sonar-jenkins
          config-drive: true
          min-ram: 4096
          # The path where the SSH private key is located
          private-key: /var/lib/nodepool/.ssh/id_rsa
          username: jenkins

    nodepool_targets:
      - name: sonar-jenkins

    nodepool_secure_jenkins:
      - name: sonar-jenkins
        user: <jenkins_api_user>
        apikey: <jenkins_api_key>
        url: https://sonar-jenkins.domain.com/
        credentials: <jenkins_credential_id>

    nodepool_openstack_clouds:
      sonar-jenkins:
        auth:
          auth_url: <openstack_auth_url>
          username: <openstack_username>
          password: <openstack_password>
          project_name: <openstack_project_name>

.. _nodepool: https://docs.openstack.org/infra/nodepool/
