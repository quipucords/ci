# ci

This repository contains ansible scripts and jenkins-job-builder definitions
used by the quipucords team.

It is advisable to install jenkins-job-builder in a virtual environment.

# Installing dependencies in a virtual environment
```
 $ git clone https://github.com/quipucords/ci.git
 $ cd ci
 $ python3.6 -m venv ~/envs/quipu-jjb-env/
 $ pip install -r requirements.txt
```

Note this does not install ansible, as it requires other system packages available through your
package manager (yum, dnf, apt, ...).
