package franz

import zio.ZIO

final case class FranzContext(_writer : DynamicProducer, _reader : BatchedStream) {
  export _writer.*
  export _reader.*
}

object FranzContext {

}
