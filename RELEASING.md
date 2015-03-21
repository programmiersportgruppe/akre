Releasing
=========

A new version of Akre is released with a command invocation like

~~~ {.sh}
./release.sh --minor
~~~

This will make sure all changes are committed and pushed,
derive a new version number from the last release number,
build the code,
run the tests,
check for binary compatibility with the previous release,
PGP sign the Maven artifacts (a Maven Central requirement),
publish the artifacts to Maven Central,
tag the current commit,
update this `README.md` with the new version number,
and push.


PGP Signing
-----------

In order for the PGP signing to work, you will need an appropriate [sbt-pgp] setup in `~/.sbt/0.13/`.
Start by adding the plugin in `~/.sbt/0.13/plugins/gpg.sbt`:

[sbt-pgp]: http://www.scala-sbt.org/sbt-pgp/

~~~ {.scala}
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
~~~

Then use one of the configuration alternatives below.

### Using GPG 2

On my Mac, I have opted for using [GPG 2].
I installed GPG 2 using [Homebrew]: `brew update && brew install gpg2`,
then setup my private key in GPG and published the public key to a keyserver.
I then added configuration in `~/.sbt/0.13/gpg.sbt` to have sbt-pgp use the `gpg2` command:

[GPG 2]: https://www.gnupg.org/
[Homebrew]: http://brew.sh/

~~~ {.scala}
import com.typesafe.sbt.SbtPgp.autoImport._

useGpg := true

PgpKeys.gpgCommand in Global := "gpg2"
~~~

### Using BouncyCastle

Alternatively, you can use the default pure-JVM BouncyCastle PGP signing.
You will need to configure your keys in `~/.sbt/0.13/gpg.sbt`:

~~~ {.scala}
import com.typesafe.sbt.SbtPgp.autoImport._

pgpSecretRing := Path.userHome / ".gnupg" / "secring.gpg"

pgpPublicRing := Path.userHome / ".gnupg" / "pubring.pgp"

// Your passphrase as array of Char (None => prompt for passphrase, Some(Array()) => no passphrase)
pgpPassphrase := Some(Array('S', 'e', 'c', 'r', 'e', 't'))
~~~ {.scala}


Publishing to Sonotype Nexus Repository
---------------------------------------

In order to publish to the Sonatype Nexus Repository, you will need to configure your Sonotype credentials,
such as by adding something like this to `.sbt/0.13/sonatype.sbt`:

~~~ {.scala}
credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  "Your Sonotype user name",
  "Your Sonotype password"
)
~~~
