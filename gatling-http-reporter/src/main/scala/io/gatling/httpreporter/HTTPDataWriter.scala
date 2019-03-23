/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.httpreporter

import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.{Http, model}
import akka.http.scaladsl.model._
import io.gatling.commons.stats.{KO_CLIENT, Status}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.Collections._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.stats.writer._
import io.gatling.core.util.NameGen
import io.gatling.httpreporter.types._
import org.json4s._
import org.json4s.jackson.Serialization

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

case class UsersAndMetrics(auth: Authentication, time: Long, users: Map[String, UserBreakdown], metrics: Map[String, MetricByStatus], metricsTotal: Map[String, MetricByStatus])
case class Percentiles(auth: Authentication, time: Long, p1: Double, p2: Double, p3: Double, p4: Double)
case class Authentication(testId: String, token: String = "")

case class HTTPData(
    auth:                       Authentication,
    stopURL:                    String,
    reporterURL:                String,
    var requestsByPath:         mutable.Map[String, RequestMetricsBuffer],
    usersByScenario:            mutable.Map[String, UserBreakdownBuffer],
    requestsByPathTotal:        mutable.Map[String, RequestMetricsBuffer],
    format:                     HTTPPathPattern,
    requestsByPathByTimeBucket: mutable.Map[Long, mutable.Map[String, RequestMetricsBuffer]] = mutable.Map(),
    var bucketTimes:            List[Long] = List(),
    var bucketTimesToResend:    mutable.Set[Long] = mutable.Set()
) extends DataWriterData

private[gatling] class HTTPDataWriter(clock: Clock, configuration: GatlingConfiguration) extends DataWriter[HTTPData] with NameGen {

  def newResponseMetricsBuffer: RequestMetricsBuffer =
    new HistogramRequestMetricsBuffer(configuration)

  private val flushTimerName = "flushTimer"
  implicit val formats = Serialization.formats(NoTypeHints)

  def onInit(init: Init): HTTPData = {
    import init._
    val requestsByPath = mutable.Map.empty[String, RequestMetricsBuffer]
    val usersByScenario = mutable.Map.empty[String, UserBreakdownBuffer]
    val requestsByPathTotal = mutable.Map.empty[String, RequestMetricsBuffer]

    val pattern: HTTPPathPattern = new OldHTTPPathPattern(runMessage, configuration)

    usersByScenario.update("allRequests", new UserBreakdownBuffer(scenarios.sumBy(_.totalUserCount.getOrElse(0L))))
    scenarios.foreach(scenario => usersByScenario += (scenario.name -> new UserBreakdownBuffer(scenario.totalUserCount.getOrElse(0L))))

    setTimer(flushTimerName, Flush, configuration.data.http.writePeriod, repeat = true)
    val auth = Authentication(init.runMessage.runDescription)
    val p = Percentiles(auth, clock.nowSeconds, configuration.charting.indicators.percentile1, configuration.charting.indicators.percentile2, configuration.charting.indicators.percentile3, configuration.charting.indicators.percentile4)
    val reqEntity = HttpEntity(ContentTypes.`application/json`, Serialization.write(p))
    val startUrl = configuration.data.http.startUrl
    val responseFuture = Http().singleRequest(HttpRequest(uri = startUrl, method = HttpMethods.POST, entity = reqEntity))
    responseFuture.onComplete {
      case Failure(ex)                                    => logger.error(s"Failed to post metrics. Check gatling.data.http.startUrl conf: ${startUrl}", ex)
      case Success(response) if response.status.isFailure => logger.error(s"Error sending request to ${startUrl}: ${response.status}")
      case Success(_)                                     => logger.debug(s"metric sent")
    }
    val reportUrl = configuration.data.http.reportUrl
    val stopUrl = configuration.data.http.stopUrl
    HTTPData(auth, stopUrl, reportUrl, requestsByPath, usersByScenario, requestsByPathTotal, pattern)
  }

  def onFlush(data: HTTPData): Unit = {
    val time = clock.nowMillis
    //Update the list of bucket time
    data.bucketTimes = time +: data.bucketTimes

    val requestsMetrics = data.requestsByPath.mapValues(_.metricsByStatus).toMap
    val usersBreakdowns = data.usersByScenario.mapValues(_.breakDown).toMap
    val requestsMetricsTotal = data.requestsByPathTotal.mapValues(_.metricsByStatus).toMap

    // Save the histogram of the current bucket in the bucket map
    data.requestsByPathByTimeBucket(time) = data.requestsByPath
    // Reset all metrics
    data.requestsByPath = mutable.Map.empty[String, RequestMetricsBuffer]
    //data.requestsByPath.foreach { case (_, buff) => buff.clear() }

    val metrics = UsersAndMetrics(data.auth, time, usersBreakdowns, requestsMetrics, requestsMetricsTotal)
    //Update the previous buckets if any.
    val metricsToUpdate = data.bucketTimesToResend.map(bucketTime => {
      val requestsByPath = data.requestsByPathByTimeBucket(bucketTime)
      val bucketRequestsMetrics = requestsByPath.mapValues(_.metricsByStatus).toMap
      UsersAndMetrics(data.auth, bucketTime, null, bucketRequestsMetrics, null)
    }).toList
    data.bucketTimesToResend.clear()
    sendMetricsToHTTP(data, metrics :: metricsToUpdate)
  }

  private def onUserMessage(userMessage: UserMessage, data: HTTPData): Unit = {
    import data._
    usersByScenario(userMessage.session.scenario).add(userMessage)
    usersByScenario("allRequests").add(userMessage)
  }


  private def onResponseMessage(response: ResponseMessage, data: HTTPData): Unit = {
    //Improve stats by tracking gatling timeouts
    val status = if(response.message.isDefined && response.message.get.startsWith("i.g.h.c.i.RequestTimeoutException")){
      KO_CLIENT
    } else {
      response.status
    }
    val responseTime = ResponseTimings.responseTime(response.startTimestamp, response.endTimestamp)
    //We have a big response time. We might need to impact the previous bucket
    //for example if we receive a 10sec responseTime, we also need to update the previous bucket max latencies
    val halfPeriod = configuration.data.http.writePeriod._1 / 2
    if(responseTime > halfPeriod){
      //If the request starts before the middle of the previous bucket, we add it to this bucket
      data.bucketTimes.iterator.takeWhile(bucketEndTime => response.startTimestamp < bucketEndTime - halfPeriod).foreach(bucketTime => {
        log.debug("slow request detected. Update previous bucket for more accurate real-time results")
        data.bucketTimesToResend.add(bucketTime)
        if (!configuration.data.http.light) {
          data.requestsByPathByTimeBucket(bucketTime).getOrElseUpdate(response.scenario, newResponseMetricsBuffer).add(status, responseTime)
        }
        data.requestsByPathByTimeBucket(bucketTime).getOrElseUpdate("allRequests", newResponseMetricsBuffer).add(status, responseTime)
      })
    }
    //Update the current bucket.
    if (!configuration.data.http.light) {
      data.requestsByPath.getOrElseUpdate(response.scenario, newResponseMetricsBuffer).add(status, responseTime)
      data.requestsByPathTotal.getOrElseUpdate(response.scenario, newResponseMetricsBuffer).add(status, responseTime)
    }
    data.requestsByPath.getOrElseUpdate("allRequests", newResponseMetricsBuffer).add(status, responseTime)
    data.requestsByPathTotal.getOrElseUpdate("allRequests", newResponseMetricsBuffer).add(status, responseTime)
  }

  override def onMessage(message: LoadEventMessage, data: HTTPData): Unit = {
    message match {
      case user: UserMessage         => onUserMessage(user, data)
      case response: ResponseMessage => onResponseMessage(response, data)
      case _                         =>
    }
  }

  override def onCrash(cause: String, data: HTTPData): Unit = {
    val reqEntity = HttpEntity(ContentTypes.`application/json`, Serialization.write(data.auth))
    val responseFuture = Http().singleRequest(HttpRequest(uri = data.stopURL, method = HttpMethods.POST, entity = reqEntity))
    responseFuture.onComplete {
      case Failure(ex)                                    => logger.error(s"Failed to stop test. Check gatling.data.http.stopURL ${data.stopURL} conf", ex)
      case Success(response) if response.status.isFailure => logger.error(s"Error sending request to ${data.stopURL}: ${response.status}")
      case Success(response)                              => logger.debug(s"metric sent")
    }
  }

  def onStop(data: HTTPData): Unit = {
    val reqEntity = HttpEntity(ContentTypes.`application/json`, Serialization.write(data.auth))
    val responseFuture: Future[model.HttpResponse] = Http().singleRequest(HttpRequest(uri = data.stopURL, method = HttpMethods.POST, entity = reqEntity))
    responseFuture.onComplete {
      case Failure(ex) => logger.error(s"Failed to stop test to ${data.stopURL}. Check gatling.data.http.stopURL conf", ex)
      case Success(response) if response.status.isFailure => logger.error(s"Error sending request to ${data.stopURL}: ${response.status}")
      case _ => logger.debug(s"metric sent")
    }
    cancelTimer(flushTimerName)
  }

  private def sendMetricsToHTTP(
    data:                 HTTPData,
    userAndMetrics:       List[UsersAndMetrics]
  ): Unit = {
    val metricsSerialized = Serialization.write(userAndMetrics)

    val reqEntity = HttpEntity(ContentTypes.`application/json`, metricsSerialized)

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      uri = data.reporterURL,
      method = HttpMethods.POST, entity = reqEntity
    ))
    responseFuture.onComplete {
      case Failure(ex) => logger.error(s"Failed to post metrics. Check gatling.data.http.reportUrl conf", ex)
      case Success(response) if response.status.isFailure => logger.error(s"Error sending request to ${data.reporterURL}: ${response.status}")
      case _ => logger.debug(s"metric sent")
    }
  }
}
