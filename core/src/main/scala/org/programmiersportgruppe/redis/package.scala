package org.programmiersportgruppe

import scala.language.implicitConversions

import akka.util.ByteString


package object redis {

  implicit def string2ByteString(s: String): ByteString = ByteString(s)

}
