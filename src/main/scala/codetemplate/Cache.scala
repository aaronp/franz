package codetemplate

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

/**
  * A very basic cache of some resource [[V]] which an be created based on an input stream
  *
  * @param compiler the compiler function - compile some expression into a value of type V
  * @param default
  * @tparam V
  */
class Cache[V](compiler: String => Try[V], default: Try[V] = Failure[V](new IllegalArgumentException("no default provided for empty script"))) {
  private object Lock

  private lazy val logger = LoggerFactory.getLogger(getClass)
  private var thunkByCode = Map[String, V]()

  def map[A](thunk: V => A): Cache[A] = new Cache[A](compiler.andThen(_.map(thunk)))

  private def createUnsafe(expression: String): Try[V] = {
    val result = compiler(expression)

    logger.debug(s"""Compiling:
        |${expression}
        |
        |Yields: ${result.isSuccess}
        |""".stripMargin)
    result.map { value =>
      thunkByCode = thunkByCode.updated(expression, value)
      value
    }
  }

  def apply(expression: String): Try[V] = {
    if (Option(expression).map(_.trim).exists(_.nonEmpty)) {
      Lock.synchronized {
        thunkByCode.get(expression) match {
          case None         => createUnsafe(expression)
          case Some(cached) => Success(cached)
        }
      }
    } else {
      default
    }
  }
}
