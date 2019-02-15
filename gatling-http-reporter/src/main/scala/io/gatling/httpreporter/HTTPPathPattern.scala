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

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.writer.RunMessage
import io.gatling.httpreporter.types._

abstract class HTTPPathPattern(runMessage: RunMessage, configuration: GatlingConfiguration) {

  def allUsersPath: HTTPPath
  def usersPath(scenario: String): HTTPPath
  def allResponsesPath: HTTPPath
  def responsePath(requestName: String, groups: List[String]): HTTPPath

  def metrics(userBreakdowns: Map[HTTPPath, UserBreakdown], responseMetricsByStatus: Map[HTTPPath, MetricByStatus]): Iterator[(String, Long)] = {

    val userMetrics = userBreakdowns.iterator.flatMap(byProgress)

    val targetResponseMetrics =
      if (configuration.data.http.light)
        responseMetricsByStatus.get(allResponsesPath).map(m => Iterator.single(allResponsesPath -> m)).getOrElse(Iterator.empty)
      else
        responseMetricsByStatus.iterator

    val responseMetrics = targetResponseMetrics.flatMap(byStatus).flatMap(byMetric)

    (userMetrics ++ responseMetrics)
      .map { case (path, value) => (metricRootPath / path).pathKey -> value }
  }

  private def byProgress(metricsEntry: (HTTPPath, UserBreakdown)): Seq[(HTTPPath, Long)] = {
    val (path, usersBreakdown) = metricsEntry
    Seq(
      activeUsers(path) -> usersBreakdown.active,
      waitingUsers(path) -> usersBreakdown.waiting,
      doneUsers(path) -> usersBreakdown.done
    )
  }

  private def byStatus(metricsEntry: (HTTPPath, MetricByStatus)): Seq[(HTTPPath, Option[Metrics])] = {
    val (path, metricByStatus) = metricsEntry
    Seq(
      okResponses(path) -> metricByStatus.ok,
      koResponses(path) -> metricByStatus.ko,
      allResponses(path) -> metricByStatus.all
    )
  }

  private def byMetric(metricsEntry: (HTTPPath, Option[Metrics])): Seq[(HTTPPath, Long)] =
    metricsEntry match {
      case (path, None) => Seq(count(path) -> 0)
      case (path, Some(m)) =>
        Seq(
          count(path) -> m.count,
          min(path) -> m.min,
          max(path) -> m.max,
          mean(path) -> m.mean,
          stdDev(path) -> m.stdDev,
          percentiles1(path) -> m.percentile1,
          percentiles2(path) -> m.percentile2,
          percentiles3(path) -> m.percentile3,
          percentiles4(path) -> m.percentile4
        )
    }

  protected def metricRootPath: HTTPPath
  protected def activeUsers(path: HTTPPath): HTTPPath
  protected def waitingUsers(path: HTTPPath): HTTPPath
  protected def doneUsers(path: HTTPPath): HTTPPath
  protected def okResponses(path: HTTPPath): HTTPPath
  protected def koResponses(path: HTTPPath): HTTPPath
  protected def allResponses(path: HTTPPath): HTTPPath
  protected def count(path: HTTPPath): HTTPPath
  protected def min(path: HTTPPath): HTTPPath
  protected def max(path: HTTPPath): HTTPPath
  protected def mean(path: HTTPPath): HTTPPath
  protected def stdDev(path: HTTPPath): HTTPPath
  protected def percentiles1(path: HTTPPath): HTTPPath
  protected def percentiles2(path: HTTPPath): HTTPPath
  protected def percentiles3(path: HTTPPath): HTTPPath
  protected def percentiles4(path: HTTPPath): HTTPPath
}
