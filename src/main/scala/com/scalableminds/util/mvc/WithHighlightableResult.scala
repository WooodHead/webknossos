/*
* Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
*/
package com.scalableminds.util.mvc

import play.api.mvc.SimpleResult
import play.api.http.HeaderNames._

trait WithHighlightableResult {

  implicit class HighlightableResult(r: SimpleResult) {
    def highlighting(elementId: String) = {
      val location = r.header.headers.get(LOCATION) getOrElse ""
      r.withHeaders(LOCATION -> s"$location#$elementId")
    }
  }

}