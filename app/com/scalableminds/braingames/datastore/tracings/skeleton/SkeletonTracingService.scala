/*
 * Copyright (C) 2011-2017 scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package com.scalableminds.braingames.datastore.tracings.skeleton

import java.io.File

import com.google.inject.Inject
import com.scalableminds.braingames.binary.storage.kvstore.{KeyValueStoreImplicits, VersionedKeyValuePair}
import com.scalableminds.braingames.datastore.tracings.skeleton.elements.SkeletonTracingDepr
import com.scalableminds.braingames.datastore.tracings.{TemporaryTracingStore, TracingDataStore, TracingService, TracingType}
import com.scalableminds.util.geometry.BoundingBox
import com.scalableminds.util.io.ZipIO
import com.scalableminds.util.tools.{Fox, FoxImplicits, TextUtils}
import net.liftweb.common.{Box, Empty, Failure, Full}
import play.api.libs.concurrent.Execution.Implicits._

import scala.reflect._


class SkeletonTracingService @Inject()(
                                        tracingDataStore: TracingDataStore,
                                        val temporaryTracingStore: TemporaryTracingStore[SkeletonTracingDepr]
                                      ) extends TracingService[SkeletonTracingDepr] with KeyValueStoreImplicits with FoxImplicits with TextUtils {

  implicit val tracingFormat = SkeletonTracingDepr.jsonFormat

  implicit val tag = classTag[SkeletonTracingDepr]

  val tracingType = TracingType.skeleton

  val tracingStore = tracingDataStore.skeletons

  def saveUpdates(tracingId: String, updateActionGroups: List[SkeletonUpdateActionGroup]): Fox[List[_]] = {
    Fox.combined(for {
      updateActionGroup <- updateActionGroups
    } yield {
      tracingDataStore.skeletonUpdates.put(tracingId, updateActionGroup.version, updateActionGroup.actions)
    })
  }

  override def applyPendingUpdates(tracing: SkeletonTracingDepr, tracingId: String, desiredVersion: Option[Long]): Fox[SkeletonTracingDepr] = {
    val existingVersion = tracing.version
    findDesiredOrNewestPossibleVersion(tracing, tracingId, desiredVersion).flatMap { newVersion =>
      if (newVersion > existingVersion) {
        val pendingUpdates = findPendingUpdates(tracingId, existingVersion, newVersion)
        for {
          updatedTracing <- update(tracing, tracingId, pendingUpdates, newVersion)
        } yield {
          save(updatedTracing, tracingId, newVersion)
          updatedTracing
        }
      } else {
        Full(tracing)
      }
    }
  }

  private def findDesiredOrNewestPossibleVersion(tracing: SkeletonTracingDepr, tracingId: String, desiredVersion: Option[Long]): Fox[Long] = {
    (for {
      newestUpdate <- tracingDataStore.skeletonUpdates.get(tracingId)
    } yield {
      desiredVersion match {
        case None => newestUpdate.version
        case Some(desiredSome) => math.min(desiredSome, newestUpdate.version)
      }
    }).getOrElse(tracing.version) //if there are no updates at all, assume tracing was created from NML
  }

  private def findPendingUpdates(tracingId: String, existingVersion: Long, desiredVersion: Long): List[SkeletonUpdateAction] = {
    def toListIter(versionIterator: Iterator[VersionedKeyValuePair[List[SkeletonUpdateAction]]],
                   acc: List[List[SkeletonUpdateAction]]): List[List[SkeletonUpdateAction]] = {
      if (!versionIterator.hasNext) acc
      else {
        val item = versionIterator.next()
        if (item.version <= existingVersion) acc
        else toListIter(versionIterator, item.value :: acc)
      }
    }

    if (desiredVersion == existingVersion) List()
    else {
      val versionIterator = tracingDataStore.skeletonUpdates.scanVersions(tracingId, Some(desiredVersion))(fromJson[List[SkeletonUpdateAction]])
      toListIter(versionIterator, List()).flatten
    }
  }

  private def update(tracing: SkeletonTracingDepr, tracingId: String, updates: List[SkeletonUpdateAction], newVersion: Long): Fox[SkeletonTracingDepr] = {
    def updateIter(tracingFox: Fox[SkeletonTracingDepr], remainingUpdates: List[SkeletonUpdateAction]): Fox[SkeletonTracingDepr] = {
      tracingFox.futureBox.flatMap {
        case Empty => Fox.empty
        case Full(tracing) => {
          remainingUpdates match {
            case List() => Fox.successful(tracing)
            case RevertToVersionAction(sourceVersion) :: tail => {
              val sourceTracing = find(tracingId, Some(sourceVersion), useCache = false, applyUpdates = true)
              updateIter(sourceTracing, tail)
            }
            case update :: tail => updateIter(Full(update.applyOn(tracing)), tail)
          }
        }
        case _ => tracingFox
      }
    }

    updates match {
      case List() => Full(tracing)
      case head :: tail => {
        for {
          updated <- updateIter(Some(tracing), updates)
        } yield updated.copy(version = newVersion)
      }
    }
  }

  def duplicate(tracing: SkeletonTracingDepr): Fox[String] = {
    val newTracing = tracing.copy(timestamp = System.currentTimeMillis(), version = 0)
    val newTracingId = createNewId
    for {
      _ <- save(newTracing, createNewId, newTracing.version)
    } yield newTracingId
  }

  private def mergeTwo(tracingA: SkeletonTracingDepr, tracingB: SkeletonTracingDepr) = {
    def mergeBoundingBoxes(aOpt: Option[BoundingBox], bOpt: Option[BoundingBox]) =
      for {
        a <- aOpt
        b <- bOpt
      } yield a.combineWith(b)

    val nodeMapping = TreeUtils.calculateNodeMapping(tracingA.trees, tracingB.trees)
    val mergedTrees = TreeUtils.mergeTrees(tracingA.trees, tracingB.trees, nodeMapping)
    val mergedBoundingBoxes = mergeBoundingBoxes(tracingA.boundingBox, tracingB.boundingBox)
    tracingA.copy(trees = mergedTrees, boundingBox = mergedBoundingBoxes, version = 0)
  }

  def merge(tracings: List[SkeletonTracingDepr], newId: String = createNewId): SkeletonTracingDepr =
    tracings.reduceLeft(mergeTwo)

  //TODO: move to wk
  def extractAllFromZip(zipfile: Option[File]): Box[List[SkeletonTracingDepr]] = {
    def isFailure[T](box: Box[T]) = {
      box match {
        case Failure(msg, _, _) => true
        case _ => false
      }
    }
    def findFailure[T](boxList: List[Box[T]]) = boxList.find(box => isFailure(box))

    def unzip(file: File) = {
      val boxOfBoxes: Box[List[Box[SkeletonTracingDepr]]] = ZipIO.withUnziped(file) {
        case (filePath, inputStream) => {
          val isNml = filePath.toString.toLowerCase.endsWith(".nml")
          if (!isNml) Empty
          else {
            NmlParser.parse(filePath.getFileName.toString, inputStream)
          }
        }
      }
      boxOfBoxes match {
        case Full(tracings: List[Box[SkeletonTracingDepr]]) => {
          val firstFailure = findFailure(tracings)
          firstFailure match {
            case Some(Failure(msg, _, _)) => Failure("Failed to parse an NML in zipfile: " + msg)
            case _ => Full(tracings.flatten)
          }
        }
        case _ => Failure("Could not unpack zipfile")
      }
    }

    zipfile match {
      case None => Failure("Empty or No zipfile")
      case Some(file) => unzip(file)
    }
  }

}
