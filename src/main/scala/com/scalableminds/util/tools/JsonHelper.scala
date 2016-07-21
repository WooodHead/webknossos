/*
 * Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package com.scalableminds.util.tools

import java.io.FileNotFoundException
import java.nio.file._

import com.typesafe.scalalogging.LazyLogging
import net.liftweb.common._
import play.api.libs.json._

import scala.io.Source

object JsonHelper extends LazyLogging {

  def jsonFromFile(path: Path, rootPath: Path): Box[JsValue] = {
    if (Files.exists(path) && !Files.isDirectory(path))
      parseJsonFromFile(path, rootPath)
    else
      Failure("Invalid path for json parsing.")
  }

  private def parseJsonFromFile(path: Path, rootPath: Path): Box[JsValue] =
    try {
      Full(Json.parse(Source.fromFile(path.toFile).getLines.mkString))
    } catch {
      case e: java.io.EOFException =>
        logger.error("EOFException in JsonHelper while trying to extract json from file.", e)
        Failure(s"An EOF exception occurred during json read. File: ${rootPath.relativize(path).toString }")
      case _: AccessDeniedException | _: FileNotFoundException =>
        logger.error("File access exception in JsonHelper while trying to extract json from file.")
        Failure(s"Failed to parse Json in '${rootPath.relativize(path).toString }'. Access denied.")
      case e: com.fasterxml.jackson.databind.JsonMappingException =>
        logger.warn(s"Exception in JsonHelper while trying to extract json from file. Path: $path. Json Mapping issue.")
        Failure(s"Cause: ${e.getCause }")
      case e: Exception =>
        logger.error(s"Exception in JsonHelper while trying to extract json from file. Path: $path. Cause: ${e.getCause}")
        Failure(s"Cause: ${e.getCause }")
    }
}