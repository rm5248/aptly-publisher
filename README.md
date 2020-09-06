# Aptly Publisher

Publish Debian packages using Aptly!

To use:

1. Install aptly
2. Install the plugin
3. On the 'Configure System' page of Jenkins, go down to the 'Aptly Publisher' section
4. Add a repository.  Each repo needs a unique name and a GPG key to sign it with.
5. Go to a project that you want to publish Debian packages for.  The files that
you want to publish must have been archived first.  Works well with the [Debian Pbuilder](https://wiki.jenkins.io/display/JENKINS/Debian+Pbuilder+Plugin) plugin.
6. Add a post-build step, 'Publish debian packages via Aptly'
7. Select the repository that you chose in step 4.
8. Build!


# TODO

1. Need to figure out a more secure way of storing the keys.  Ideally, the private key should be on a separate machine that we simply talk to along the lines of "here go sign this for me".  Something similar with agent forwarding: https://mlohr.com/gpg-agent-forwarding/
