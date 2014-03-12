package models.user.time

import java.util.{Calendar, Date}
import models.annotation.AnnotationLike
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import play.modules.reactivemongo.json.BSONFormats._
/**
 * Company: scalableminds
 * User: tmbo
 * Date: 28.10.13
 * Time: 00:58
 */
case class TimeSpan(time: Long, timestamp: Long, lastUpdate: Long, _user: BSONObjectID, note: Option[String] = None, annotation: Option[String] = None, _id: BSONObjectID = BSONObjectID.generate) {

  val created = new Date(timestamp)

  def annotationEquals(other: String): Boolean =
    annotationEquals(Some(other))

  def annotationEquals(other: Option[String]): Boolean =
    annotation == other

  def addTime(duration: Long, timestamp: Long) =
    this.copy(time = time + duration, timestamp = timestamp)
}

object TimeSpan {
  implicit val timeSpanFormat = Json.format[TimeSpan]

  val timeRx = "(([0-9]+)d)?(\\s*([0-9]+)h)?(\\s*([0-9]+)m)?".r

  val hoursRx = "[0-9]+".r

  def create(timestamp: Long, _user: BSONObjectID, annotation: Option[AnnotationLike]) =
    TimeSpan(0, timestamp, timestamp, _user = _user, annotation = annotation.map(_.id))

  def inMillis(days: Int, hours: Int, minutes: Int) =
    (days.days + hours.hours + minutes.minutes).toMillis

  def parseTime(s: String): Option[Long] = {
    s match {
      case timeRx(_, d, _, h, _, m) if d != null || h != null || m != null =>
        Some(inMillis(d.toInt, h.toInt, m.toInt))
      case hoursRx(h) if h != null =>
        Some(inMillis(0, h.toInt, 0))
      case _ =>
        None
    }
  }

  def groupByMonth(timeSpan: TimeSpan) = {
    val cal = Calendar.getInstance
    cal.setTime(timeSpan.created)
    Month(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
  }

  def groupByWeek(timeSpan: TimeSpan) = {
    val cal = Calendar.getInstance
    cal.setTime(timeSpan.created)
    Week(cal.get(Calendar.WEEK_OF_YEAR), cal.get(Calendar.YEAR))
  }
}
