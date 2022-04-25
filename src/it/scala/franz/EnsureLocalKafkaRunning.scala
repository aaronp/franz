package franz

import org.slf4j.LoggerFactory

import java.util.concurrent.CountDownLatch
import scala.sys.process.ProcessLogger
import sys.process.stringToProcess

object EnsureLocalKafkaRunning {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]) = {
    apply()
  }

  def apply(): Unit = if !isRunningInDocker() then startKafka()

  def startLocalKafkaPath = java.nio.file.Paths.get("startLocalKafka.sh").toAbsolutePath.toString

  def startKafka(): Unit =
    logger.info(
      """

                    $$\                          $$\     $$\                           $$\                 $$$$$$\  $$\
                    $$ |                         $$ |    \__|                          $$ |               $$  __$$\ $$ |
         $$$$$$$\ $$$$$$\    $$$$$$\   $$$$$$\ $$$$$$\   $$\ $$$$$$$\   $$$$$$\        $$ |  $$\ $$$$$$\  $$ /  \__|$$ |  $$\ $$$$$$\
        $$  _____|\_$$  _|   \____$$\ $$  __$$\\_$$  _|  $$ |$$  __$$\ $$  __$$\       $$ | $$  |\____$$\ $$$$\     $$ | $$  |\____$$\
        \$$$$$$\    $$ |     $$$$$$$ |$$ |  \__| $$ |    $$ |$$ |  $$ |$$ /  $$ |      $$$$$$  / $$$$$$$ |$$  _|    $$$$$$  / $$$$$$$ |
         \____$$\   $$ |$$\ $$  __$$ |$$ |       $$ |$$\ $$ |$$ |  $$ |$$ |  $$ |      $$  _$$< $$  __$$ |$$ |      $$  _$$< $$  __$$ |
        $$$$$$$  |  \$$$$  |\$$$$$$$ |$$ |       \$$$$  |$$ |$$ |  $$ |\$$$$$$$ |      $$ | \$$\\$$$$$$$ |$$ |      $$ | \$$\\$$$$$$$ |
        \_______/    \____/  \_______|\__|        \____/ \__|\__|  \__| \____$$ |      \__|  \__|\_______|\__|      \__|  \__|\_______|
                                                                       $$\   $$ |
                                                                       \$$$$$$  |
                                                                        \______/
        """)
    //entered RUNNING state, process has stayed up for > than 1 seconds
    var requiredComponents = Set("broker", "schema-registry", "zookeeper")
    val countdown = new CountDownLatch(requiredComponents.size)

    def logLine(line: String) = {
      if line.contains("entered RUNNING state, process has stayed up for > than 1 seconds") then {
        requiredComponents.find(line.contains).foreach { component =>
          requiredComponents = requiredComponents - component
          logger.info(s"$component started, STILL WAITING FOR $requiredComponents".toUpperCase)
          countdown.countDown()
        }
      }
      logger.info(s"KAFKA: $line")
    }

    val process = startLocalKafkaPath.run(ProcessLogger(logLine))
    logger.info(s"Started Kafka, is running is ${process.isAlive()}, waiting for $requiredComponents")
    countdown.await()
    logger.info("We're all set! ... though kafka isn't typically actually up now, so tests will likely fail")

  def isRunningInDocker(): Boolean =
    ("docker ps".!!).linesIterator.exists(_.contains("lensesio/fast-data-dev"))
}
