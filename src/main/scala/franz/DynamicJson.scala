package franz

import io.circe.{Decoder, Encoder, Json}
import org.apache.avro.generic.GenericRecord

import scala.annotation.targetName
import scala.language.implicitConversions
import scala.language.dynamics
import scala.util.Try

/**
  * Like circe optics  - pimped dynamic type on top of circe json
  */
case class DynamicJson(underlyingJson: Json) extends Dynamic {

  def asAvro(namespace :String = "gen"): GenericRecord = {
    import SchemaGen.*
    underlyingJson.asAvro(namespace)
  }

  @targetName("merge")
  def +(other: Json) = copy(underlyingJson.deepMerge(other))

  override def toString: String = asString

  def asBool(default: Boolean = false) = underlyingJson.asBoolean.getOrElse(default)
  def asString: String                 = underlyingJson.asString.getOrElse(underlyingJson.noSpaces)

  def asIntOpt                = underlyingJson.asNumber.flatMap(_.toInt)
  def asInt(default: Int = 0) = asIntOpt.getOrElse(default)

  def asDoubleOpt: Option[Double]   = underlyingJson.asNumber.map(_.toDouble)
  def asDouble(default: Double = 0) = asDoubleOpt.getOrElse(default)

  def as[A: Decoder]: Try[A] = underlyingJson.as[A].toTry

  final def each: Vector[DynamicJson] = {
    underlyingJson.asArray.getOrElse(Vector.empty).map(DynamicJson.apply)
  }

  final def selectDynamic(field: String): DynamicJson = apply(field)

  def apply(field: String): DynamicJson = {
    val selected = underlyingJson.asObject.fold(Json.Null) { obj =>
      obj(field).getOrElse(Json.Null)
    }
    DynamicJson(selected)
  }

  final def apply(index: Int): DynamicJson = {
    val selected = underlyingJson.asArray.fold(Json.Null) { arr =>
      arr.lift(index).getOrElse(Json.Null)
    }
    DynamicJson(selected)
  }
}

object DynamicJson {

  trait LowPriority {
    implicit def asJson(value: DynamicJson): Json                    = value.underlyingJson
    implicit def asDynamic(json: Json): DynamicJson                  = DynamicJson(json)
    implicit def asVector(json: Json): Vector[DynamicJson]           = asDynamic(json).each
    implicit def asVector(dynamic: DynamicJson): Vector[DynamicJson] = dynamic.underlyingJson.asArray.map(_.map(DynamicJson.apply)).getOrElse(Vector(dynamic))
  }

  implicit val encoder: Encoder[DynamicJson] = Encoder[DynamicJson](_.underlyingJson)
  implicit val decoder: Decoder[DynamicJson] = Decoder.decodeJson.map(DynamicJson.apply)

  object implicits extends LowPriority {
    implicit class Syntax(val json: Json) extends AnyVal {
      def asDynamic = DynamicJson(json)
    }
  }
}
