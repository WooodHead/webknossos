package brainflight

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import scala.collection.JavaConversions._

object ActorSystems {
  lazy val dataRequestSystem = 
    ActorSystem("DataRequests", ConfigFactory.load().getConfig("datarequest"))
}