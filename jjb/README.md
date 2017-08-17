Jenkins Job Builder
===================

Job Definition Guidelines
-------------------------

When defining jobs in this directory, please organize files according to the
following:

* Job defaults go in `jobs/defaults.yaml`.
* Macros go in `jobs/macros.yaml`.
* Templates go in in files named after to the jobs they create, so they can be
  easily found.
* Jobs using those templates are defined in `jobs/projects.yaml`, and the
  name of the project should match the name of the file containing that
  project's jobs.

To put it another way, `jobs/defaults.yaml`, `jobs/macros.yaml` and
`jobs/projects.yaml` are special files that must exist. All other yaml files
contain jobs, job groups, and job templates. File names should prefer dashes
instead of underscores, preferably not using any underscores at all.

When creating a new build step, always check to see if that build step exists
as a macro, or as a build step in another job. If it already exists as a macro,
use the macro. If it already exists as a build step in another job, turn it
into a macro, then use the macro in both jobs.

When creating a new job file, make sure a project exists with name of the yaml
file (without the `.yaml` extension). This ensures that
`jobs/projects.yaml` is the definitive place to go to find out where a job is
defined.

When creating a new job template, try not to make the first piece of the job
name an expanded template variable. This is a minor point, but it makes finding
the job template used to create a "real" Jenkins job a little bit simpler.
Related to this, try to make job names obviously correspond to their project
name, and therefore their yaml file name.

When creating any new macro, please use descriptive and accurate names so that
you don't necessarily need, for example, to consult a macro definition to know
what a macro does.

JJB Configuration
-----------------

JJB requires a configuration file to function. It searches for a configuration
file in the following locations, in order:

1. `~/.config/jenkins_jobs/jenkins_jobs.ini`
2. `/etc/jenkins_jobs/jenkins_jobs.ini`

Thus, an easy way to configure JJB is as follows:

```sh
install -Dm600 jenkins_jobs.example.ini ~/.config/jenkins_jobs/jenkins_jobs.ini
vim ~/.config/jenkins_jobs/jenkins_jobs.ini
```

In fact, you can place the configuration file anywhere. Have in mind that, if
you choose to place it elsewhere other than the location it searches
automatically, you will need to pass the configuration file path using the
`--conf` option. For example: `jenkins-jobs --conf path/to/jenkins_jobs.ini
...`.

For details, see the [configuration
file](https://docs.openstack.org/infra/jenkins-job-builder/execution.html)
documentation.

JJB Usage
---------

Two of the most important things one can do with JJB are to generate jobs and
print them to stdout, and to generate jobs and send them to a Jenkins
instance:

```console
$ jenkins-jobs test jobs

$ jenkins-jobs update jobs
```

You can also update a sub-set of jobs, delete jobs, remove outdated jobs, and
more. For more information, start with the [quick start
guide](https://docs.openstack.org/infra/jenkins-job-builder/quick-start.html).

SSL Issues
----------

If you don't have the CA that secures our Jenkins instance in your system CA
pack JJB will complain about SSL issues. To fix this add the CA to your system
CA pack. Alternatively, you can modify the JJB check to allow insecure usage.
Warning: allow **insecure at your own risk**.

To workaround SSL errors, patch line 431 of
`/path/to/lib/pythonX.X/site-packages/jenkins/__init__.py`:

    import ssl
    context = ssl._create_unverified_context()
    response = urlopen(req, context=context, timeout=self.timeout).read()
