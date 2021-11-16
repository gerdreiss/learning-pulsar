object SensorDomain {
  import java.util.UUID
  import scala.util.Random

  val startupTime: Long = System.currentTimeMillis

  sealed trait Status
  object Status {
    case object Stopped  extends Status
    case object Starting extends Status
    case object Running  extends Status
  }

  case class SensorEvent(
      sensorId: UUID,
      status: Status,
      startupTime: Long,
      eventTime: Long,
      reading: Double
  )

  val sensorIds: List[UUID] = List.fill(10)(UUID.randomUUID)
  val offSensors: Set[UUID] = sensorIds.toSet
  val onSensors: Set[UUID]  = Set.empty

  def generate(
      ids: List[UUID] = sensorIds,
      off: Set[UUID] = offSensors,
      on: Set[UUID] = onSensors
  ): Iterator[SensorEvent] = {
    Thread.sleep(Random.nextInt(500) + 200)

    val index    = Random.nextInt(sensorIds.size)
    val sensorId = sensorIds(index)
    val temp = if (off(sensorId)) {
      println(s"Starting sensor $index")
      0.0
    } else {
      BigDecimal(40 + Random.nextGaussian()).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    }
    val reading = SensorEvent(sensorId, Status.Running, startupTime, System.currentTimeMillis, temp)
    Iterator.single(reading) ++ generate(ids, off - sensorId, on + sensorId)
  }
} // end SensorDomain

object PulsarProducer extends App {
  import SensorDomain._
  import com.sksamuel.pulsar4s._
  import com.sksamuel.pulsar4s.circe._
  import io.circe.generic.auto._

  import java.util.concurrent.Executors
  import scala.concurrent.ExecutionContext

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  val pulsarClient: PulsarClient = PulsarClient("pulsar://172.20.0.2:6650")
  val topic: Topic               = Topic("sensor-events")
  val eventProducer: Producer[SensorEvent] = pulsarClient.producer[SensorEvent](
    ProducerConfig(
      topic,
      producerName = Some("sensor-producer"),
      enableBatching = Some(true),
      blockIfQueueFull = Some(true)
    )
  )

  SensorDomain.generate().take(100).foreach { sensorEvent =>
    val message = DefaultProducerMessage(
      key = Some(sensorEvent.sensorId.toString),
      value = sensorEvent,
      eventTime = Some(EventTime(sensorEvent.eventTime))
    )

    eventProducer.sendAsync(message)
  }

} // PulsarProducer

object PulsarConsumer extends App {
  import SensorDomain._
  import com.sksamuel.pulsar4s.circe._
  import com.sksamuel.pulsar4s.{ConsumerConfig, PulsarClient, Subscription, Topic}
  import io.circe.generic.auto._
  import org.apache.pulsar.client.api.{SubscriptionInitialPosition, SubscriptionType}

  import scala.annotation.tailrec
  import scala.util.{Failure, Success}

    val pulsarClient: PulsarClient = PulsarClient("pulsar://172.20.0.2:6650")
  val topic: Topic               = Topic("sensor-events")
  val eventConsumer = pulsarClient.consumer[SensorEvent](
    ConsumerConfig(
      subscriptionName = Subscription("sensor-event-subscription"),
      topics = Seq(topic),
      consumerName = Some("sensor-event-consumer"),
      subscriptionInitialPosition = Some(SubscriptionInitialPosition.Earliest),
      subscriptionType = Some(SubscriptionType.Exclusive)
    )
  )

  @tailrec
  def receiveAll(totalMessageCount: Int = 0): Unit =
    eventConsumer.receive match {
      case Failure(error) =>
        println(s"Failed to receive message: ${error.getMessage}")
      case Success(message) =>
        println(
          s"Total message: $totalMessageCount - Acked message ${message.messageId} - ${message.value}"
        )
        eventConsumer.acknowledge(message.messageId)
        receiveAll(totalMessageCount + 1)
    }

  receiveAll()

} // end PulsarConsumer
