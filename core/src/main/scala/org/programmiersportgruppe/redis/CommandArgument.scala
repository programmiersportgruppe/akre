package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed abstract class CommandArgument {
  def asByteString: ByteString
}

object CommandArgument {

  object ImplicitConversions {

    import scala.language.implicitConversions

    implicit def byteString2StringArgument(s: ByteString): StringArgument = StringArgument(s)
    implicit def string2StringArgument(s: String): StringArgument = StringArgument(s)
    implicit def long2IntegerArgument(l: Long): IntegerArgument = IntegerArgument(l)
  }

}


final case class Constant(override val toString: String) extends CommandArgument {
  override def asByteString = toString
}


final case class StringArgument(asByteString: ByteString) extends CommandArgument {
  override def toString = s"<bytes=${asByteString.size}>"
}

object StringArgument {
  def apply(value: String): StringArgument = new StringArgument(value)
}


final case class Key(asByteString: ByteString) extends CommandArgument {
  override def toString = asByteString.utf8String
}


final case class IntegerArgument(value: Long) extends CommandArgument {
  override def asByteString = value.toString
  override def toString = value.toString
}


final case class DoubleArgument(value: Double) extends CommandArgument {
  override def asByteString = value.toString
  override def toString = value.toString
}
