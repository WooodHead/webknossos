/*
 * Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package com.scalableminds.datastore.models

import java.nio.file.{Files, Paths}
import java.util.UUID

import com.scalableminds.braingames.binary._
import com.scalableminds.braingames.binary.models.{DataLayer, DataSource}
import com.scalableminds.util.io.PathUtils
import com.scalableminds.util.tools.{Fox, FoxImplicits}
import com.typesafe.scalalogging.LazyLogging
import net.liftweb.common.{Empty, Failure, Full}

import scala.concurrent.{Future, blocking}
import play.api.libs.concurrent.Execution.Implicits._

case class VolumeUpdate(
                         dataSource: DataSource,
                         dataLayer: DataLayer,
                         dataSection: Option[String],
                         resolution: Int,
                         cuboid: Cuboid,
                         dataFile: String)

object VolumeUpdateService extends LazyLogging with FoxImplicits{
  private def writeDataToFile(data: Array[Byte]): Fox[String] = {
    Future {
      blocking {
        try {
          val userBackupFolder = PathUtils.ensureDirectory(Paths.get("userBinaryData").resolve("logging"))
          val backupFile = userBackupFolder.resolve(UUID.randomUUID().toString + ".raw")
          val os = Files.newOutputStream(backupFile)
          os.write(data)
          os.close()
          Full(backupFile.toString)
        } catch {
          case e: Exception =>
            logger.error("Failed to write volume update to backup folder. Error: " + e)
            Failure("Failed to write volume update to backup folder.", Full(e), Empty)
        }
      }
    }
  }

  def store(request: DataWriteRequest): Fox[Boolean] = {
    writeDataToFile(request.data).map { backupLocation =>
      val update = VolumeUpdate(
        request.dataSource,
        request.dataLayer,
        request.dataSection,
        request.resolution,
        request.cuboid,
        backupLocation)
      logger.info(s"Volume update: $update")
      true
    }
  }
}
