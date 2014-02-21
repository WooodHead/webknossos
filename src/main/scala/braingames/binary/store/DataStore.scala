package braingames.binary.store

import scala.concurrent.Future
import akka.actor.Actor
import scala.util._
import scala.concurrent.ExecutionContext.Implicits._
import braingames.binary.LoadBlock
import net.liftweb.common.Box

/**
 * Abstract Datastore defines all method a binary data source (e.q. normal file
 * system or db implementation) must implement to be used
 */

class DataNotFoundException(message: String) extends Exception(s"$message Could not find the data")

abstract class DataStore extends Actor {
  /**
   * Loads the data of a given point from the data source
   */
  def load(dataInfo: LoadBlock): Future[Box[Array[Byte]]]

  def receive = {
    case request: LoadBlock =>
      val s = sender
      load(request).onComplete {
        case Failure(e) =>
          s ! e
        case Success(d) =>
          s ! d
      }
  }
}

object DataStore {

  def createFilename(dataInfo: LoadBlock) =
    "%s/%s/%s".format(
      dataInfo.dataSource.baseDir,
      dataInfo.dataLayerSection.baseDir,
      knossosFilePath(dataInfo.dataSource.id, dataInfo.resolution, dataInfo.block.x, dataInfo.block.y, dataInfo.block.z))

  def knossosFilePath(id: String, resolution: Int, x: Int, y: Int, z: Int) =
    "%d/x%04d/y%04d/z%04d/%s_mag%d_x%04d_y%04d_z%04d.raw".format(
      resolution,
      x, y, z,
      id,
      resolution,
      x, y, z)
}