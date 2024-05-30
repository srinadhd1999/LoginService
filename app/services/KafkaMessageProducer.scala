package services

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KafkaMessageProducer @Inject()(config: play.api.Configuration)(implicit ec : ExecutionContext){

  private val KafkaTopic: String = config.get[String]("kafka.topic-inventory")

  private val kafkaProducerProps: Properties = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get[String]("kafka.bootstrap.servers"))
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props

  }

  // Create Kafka producer
  private val producer = new KafkaProducer[String, String](kafkaProducerProps)

  // Method to send message asynchronously
  def sendMessage(sender: String, content: String): Future[Unit] = Future {
    val message = s"$sender: $content"
    println(s"Sending message to Kafka: $message")
    val record = new ProducerRecord[String, String](KafkaTopic, s"$sender: $content")
    println(s"Sending message: $sender - $content")
    println(s"Record: $record")
    producer.send(record)
  }
  // Add shutdown hook to close the producer when application exits
  sys.addShutdownHook {
    producer.close()
  }
}
