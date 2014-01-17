package braingames.binary

import braingames.geometry.Point3D
import akka.agent.Agent
import akka.actor._
import akka.actor.ActorSystem
import braingames.geometry.Vector3D
import braingames.util.Math._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import scala.util._
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.routing.RoundRobinRouter
import java.util.UUID
import com.typesafe.config.Config
import braingames.binary.models.DataLayer
import braingames.binary.models.DataSet
import store._
import braingames.binary.models.DataSetRepository
import scala.concurrent.Await
import braingames.binary.models.DataLayerSection
import net.liftweb.common.Box
import net.liftweb.common.{Failure => BoxFailure}
import net.liftweb.common.Full
import java.util.NoSuchElementException
import braingames.util.BlockedArray3D
import braingames.util.ExtendedTypes.ExtendedArraySeq
import braingames.util.ExtendedTypes.ExtendedDouble

class DataRequestActor(
  val conf: Config,
  val cache: Agent[Map[CachedBlock, Future[Box[Array[Byte]]]]],
  dataSetRepository: DataSetRepository)
  extends Actor
  with DataCache
  with EmptyDataProvider {

  import store.DataStore._

  val sys = context.system

  implicit val ec = context.dispatcher

  val id = UUID.randomUUID().toString()

  implicit val dataBlockLoadTimeout = Timeout((conf.getInt("loadTimeout")) seconds)

  // defines the maximum count of cached file handles
  val maxCacheSize = conf.getInt("cacheMaxSize")

  // defines how many file handles are deleted when the limit is reached
  val dropCount = conf.getInt("cacheDropCount")

  val remotePath = conf.getString("datarequest.remotepath")

  val useRemote = conf.getBoolean("useRemote")

  implicit val system =
    if (useRemote)
      ActorSystem("DataRequests", conf.getConfig("datarequest"))
    else
      context.system

  lazy val dataStores = List[ActorRef](
    actorForWithLocalFallback[FileDataStore]("fileDataStore")) //,
  //actorForWithLocalFallback[GridDataStore]("gridDataStore"),
  //system.actorOf(Props(new EmptyDataStore()).withRouter(new RoundRobinRouter(3)), s"${id}__emptyDataStore"))

  def actorForWithLocalFallback[T <: Actor](name: String)(implicit evidence: scala.reflect.ClassTag[T]) = {
    if (useRemote)
      system.actorFor(s"$remotePath/user/$name")
    else
      system.actorOf(Props[T].withRouter(new RoundRobinRouter(3)), s"${id}__${name}")
  }

  def receive = {
    case dataRequest: DataRequest =>
      val t = System.currentTimeMillis()
      val s = sender
      // This construct results in a parallel execution and catches all the errors
      Future.successful().flatMap{ _ =>
        load(dataRequest)
      }.onComplete{
        case Success(data) =>
          s ! Some(data)
        case Failure(e) =>
          System.err.println(s"DataRequestActor Error for Request. Error: $e")
          e.printStackTrace()
          s ! None
      }
    case DataRequestCollection(requests) =>
      val resultsPromise = Future.traverse(requests)(load)
      val s = sender
      resultsPromise.onComplete {
        case Success(results) =>
          s ! Some(results.appendArrays)
        case Failure(e) =>
          System.err.println(s"DataRequestActor Error for Request. Error: $e")
          e.printStackTrace()
          s ! None
      }
  }

  def loadFromLayer(loadBlock: LoadBlock): Future[Box[Array[Byte]]] = {
    if (loadBlock.dataLayerSection.doesContainBlock(loadBlock.block, loadBlock.dataSet.blockLength)) {

      def loadFromStore(dataStores: List[ActorRef]): Future[Box[Array[Byte]]] = dataStores match {
        case a :: tail =>
          (a ? loadBlock)
            .mapTo[Box[Array[Byte]]]
            .flatMap {
            dataOpt =>
              dataOpt match {
                case d: Full[Array[Byte]] =>
                  Future.successful(d)
                case _ =>
                  loadFromStore(tail)
              }
          }.recoverWith {
            case e: AskTimeoutException =>
              println(s"WARN: (${loadBlock.dataSet.name}/${loadBlock.dataLayerSection.baseDir} ${loadBlock.block}) ${a.path}: Not response in time.")
              loadFromStore(tail)
          }
        case _ =>
          Future.successful(None)
      }

      withCache(loadBlock) {
        loadFromStore(dataStores)
      }
    } else {
      Future.successful(None)
    }
  }

  def loadFromSomewhere(dataSet: DataSet, layer: DataLayer, requestedSection: Option[String], resolution: Int, block: Point3D): Future[Array[Byte]] = {

    def loadFromSections(sections: Stream[DataLayerSection]): Future[Array[Byte]] = sections match {
      case (section, layer) #:: tail =>
        val loadBlock = LoadBlock(dataSet, layer, section, resolution, block)
        loadFromLayer(loadBlock).flatMap {
          case Full(byteArray) =>
            Future.successful(byteArray)
          case net.liftweb.common.Failure(e, _, _) =>
            System.err.println("DataStore Failure: " + e)
            loadFromSections(tail)
          case _ =>
            loadFromSections(tail)
        }
      case _ =>
        Future.successful(loadNullBlock(dataSet, layer))
    }

    val sections = Stream(layer.sections.map {(_, layer)}.filter {
      section =>
        requestedSection.map( _ == section._1.sectionId) getOrElse true
    }: _*).append {
      Await.result(layer.fallback.map(dataSetRepository.findByName).getOrElse(Future.successful(None)).map(_.flatMap {
        d =>
          d.dataLayer(layer.typ).map {
            fallbackLayer =>
              if (layer.isCompatibleWith(fallbackLayer))
                fallbackLayer.sections.map {(_, fallbackLayer)}
              else {
                System.err.println("Incompatible fallback layer!")
                Nil
              }
          }
      }.getOrElse(Nil)), 5 seconds)
    }
    loadFromSections(sections)
  }

  def loadBlocks(minBlock: Point3D, maxBlock: Point3D, dataRequest: DataRequest, layer: DataLayer) = {
    val blockIdxs = for {
      x <- minBlock.x to maxBlock.x
      y <- minBlock.y to maxBlock.y
      z <- minBlock.z to maxBlock.z
    } yield Point3D(x, y, z)

    Future.traverse(blockIdxs) {
      p =>
        loadFromSomewhere(
          dataRequest.dataSet,
          layer,
          dataRequest.dataSection,
          dataRequest.resolution,
          p)
    }
  }

  def load(dataRequest: DataRequest): Future[Array[Byte]] = {

    val cube = dataRequest.cuboid

    val dataSet = dataRequest.dataSet

    val layer = dataRequest.dataLayer

    val maxCorner = cube.maxCorner

    val minCorner = cube.minCorner

    val minPoint = Point3D(math.max(roundDown(minCorner._1), 0), math.max(roundDown(minCorner._2), 0), math.max(roundDown(minCorner._3), 0))

    val minBlock =
      dataSet.pointToBlock(minPoint, dataRequest.resolution)
    val maxBlock =
      dataSet.pointToBlock(
        Point3D(roundUp(maxCorner._1), roundUp(maxCorner._2), roundUp(maxCorner._3)),
        dataRequest.resolution)

    val pointOffset = minBlock.scale(dataSet.blockLength)

    loadBlocks(minBlock, maxBlock, dataRequest, layer)
      .map {
      blocks =>
        BlockedArray3D(
          blocks.toVector,
          dataSet.blockLength, dataSet.blockLength, dataSet.blockLength,
          maxBlock.x - minBlock.x + 1, maxBlock.y - minBlock.y + 1, maxBlock.z - minBlock.z + 1,
          layer.bytesPerElement,
          0.toByte)
    }
      .map {
      block =>
        new DataBlockCutter(block, dataRequest, layer, pointOffset).cutOutRequestedData
    }
  }
}

class DataBlockCutter(block: BlockedArray3D[Byte], dataRequest: DataRequest, layer: DataLayer, offset: Point3D) {
  val dataSet = dataRequest.dataSet

  val resolution = dataRequest.resolution

  val cube = dataRequest.cuboid

  @inline
  def interpolatedData(px: Double, py: Double, pz: Double) = {
    if (dataRequest.settings.skipInterpolation)
      byteLoader(Point3D(px.castToInt, py.castToInt, pz.castToInt))
    else
      layer.interpolator.interpolate(layer.bytesPerElement, byteLoader _)(Vector3D(px, py, pz))
  }

  def cutOutRequestedData = {
    val result: Array[Byte] =
      cube.withContainingCoordinates(extendArrayBy = layer.bytesPerElement)(interpolatedData)

    if (dataRequest.settings.useHalfByte)
      convertToHalfByte(result)
    else {
      result
    }
  }

  def convertToHalfByte(a: Array[Byte]) = {
    val aSize = a.size
    val compressedSize = if (aSize % 2 == 0) aSize / 2 else aSize / 2 + 1
    val compressed = new Array[Byte](compressedSize)
    var i = 0
    while (i * 2 + 1 < aSize) {
      val first = (a(i * 2) & 0xF0).toByte
      val second = (a(i * 2 + 1) & 0xF0).toByte >> 4 & 0x0F
      val value = (first | second).asInstanceOf[Byte]
      compressed(i) = value
      i += 1
    }
    compressed
  }

  def calculatePositionInLoadedBlock(globalPoint: Point3D) = {
    dataSet.applyResolution(globalPoint, resolution).move(offset.negate)
  }

  def byteLoader(globalPoint: Point3D): Array[Byte] = {
    block(calculatePositionInLoadedBlock(globalPoint))
  }

  def nullValue(bytesPerElement: Int) =
    new Array[Byte](bytesPerElement)
}