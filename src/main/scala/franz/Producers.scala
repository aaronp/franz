package franz

//import zio.blocking.Blocking
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

object Producers {
  def instance: Any = FranzConfig().producer

//  def stringAvro = instance.map { p =>
////    p.produce(_, Serde.string, Serde.)
//    ???
//  }

}
