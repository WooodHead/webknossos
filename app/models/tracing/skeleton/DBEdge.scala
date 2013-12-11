package models.tracing.skeleton

import oxalis.nml.Edge
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Json
import models.basics.SecuredBaseDAO
import braingames.reactivemongo.DBAccessContext
import scala.concurrent.Future
import play.modules.reactivemongo.json.BSONFormats._
import play.api.libs.concurrent.Execution.Implicits._

case class DBEdge(edge: Edge, _treeId: BSONObjectID, _id: BSONObjectID = BSONObjectID.generate)

object DBEdge {
  implicit val dbEdgeFormat = Json.format[DBEdge]
}

object DBEdgeDAO extends SecuredBaseDAO[DBEdge] {

  // TODO: ensure index!
  // c.collection.ensureIndex("_treeId")

  val collectionName = "edges"

  val formatter = DBEdge.dbEdgeFormat

  def remove(edge: Edge, _tree: BSONObjectID)(implicit ctx: DBAccessContext) =
    collectionRemove(Json.obj("_treeId" -> _tree, "edge.source" -> edge.source, "edge.target" -> edge.target))

  def findByTree(_tree: BSONObjectID)(implicit ctx: DBAccessContext) =
    find(Json.obj("_treeId" -> _tree)).collect[List]()

  def findOneByTree(_tree: BSONObjectID)(implicit ctx: DBAccessContext) =
    collectionFind(Json.obj("_treeId"-> _tree)).one[DBEdge]

  def countByTree(_tree: BSONObjectID)(implicit ctx: DBAccessContext) =
    count(Json.obj("_treeId"-> _tree))

  def removeAllOf(_tree: BSONObjectID)(implicit ctx: DBAccessContext) =
    remove("_treeId", _tree)

  def removeAllOnBorder(nodeIds: List[Int], _source: BSONObjectID)(implicit ctx: DBAccessContext) =
    collectionRemove(
      Json.obj(
        "_treeId" -> _source,
        "$or" -> Json.arr(
          Json.obj("edge.source" -> Json.obj("$nin" -> nodeIds),
            "edge.target" -> Json.obj("$in" -> nodeIds)),
          Json.obj("edge.source" -> Json.obj("$in" -> nodeIds),
            "edge.target" -> Json.obj("$nin" -> nodeIds)))))

  def moveAllEdges(_source: BSONObjectID, _target: BSONObjectID)(implicit ctx: DBAccessContext) =
    collectionUpdate(
      Json.obj("_treeId" -> _source),
      Json.obj("$set" -> Json.obj(
        "_treeId" -> _target)), upsert = false, multi = true)

  def moveEdges(nodeIds: List[Int], _source: BSONObjectID, _target: BSONObjectID)(implicit ctx: DBAccessContext) =
    collectionUpdate(
      Json.obj(
        "edge.source" -> Json.obj("$in" -> nodeIds),
        "edge.target" -> Json.obj("$in" -> nodeIds),
        "_treeId" -> _source),
      Json.obj("$set" -> Json.obj(
        "_treeId" -> _target)), upsert = false, multi = true)

  def deleteEdge(edge: Edge, _tree: BSONObjectID)(implicit ctx: DBAccessContext) = {
    collectionRemove(
      Json.obj("_treeId" -> _tree, "$or" -> Json.arr(
        Json.obj("edge.source" -> edge.source, "edge.target" -> edge.target),
        Json.obj("edge.source" -> edge.target, "edge.targetcc" -> edge.source))))
  }

  def deleteEdgesOfNode(nodeId: Int, _tree: BSONObjectID)(implicit ctx: DBAccessContext) = {
    collectionRemove(
      Json.obj("_treeId" -> _tree, "$or" -> Json.arr(
        Json.obj("edge.source" -> nodeId),
        Json.obj("edge.target" -> nodeId))))
  }
}