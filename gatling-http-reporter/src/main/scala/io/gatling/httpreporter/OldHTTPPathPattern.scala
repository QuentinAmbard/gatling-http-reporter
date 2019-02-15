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

class OldHTTPPathPattern(runMessage: RunMessage, configuration: GatlingConfiguration) extends HTTPPathPattern(runMessage, configuration) {

  private def removeDecimalPart(d: Double): String = {
    val i = d.toInt
    if (d == i.toDouble) String.valueOf(i)
    else String.valueOf(d)
  }

  private val usersRootKey = HTTPPath.graphitePath("users")
  private val percentiles1Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile1)
  private val percentiles2Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile2)
  private val percentiles3Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile3)
  private val percentiles4Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile4)

  val metricRootPath = HTTPPath.graphitePath(configuration.data.graphite.rootPathPrefix) / runMessage.simulationId

  val allUsersPath = usersRootKey / "allUsers"

  def usersPath(scenario: String): HTTPPath = usersRootKey / scenario

  val allResponsesPath = HTTPPath.graphitePath("allRequests")

  def responsePath(requestName: String, groupHierarchy: List[String]) = HTTPPath.graphitePath(groupHierarchy.reverse) / requestName

  protected def activeUsers(path: HTTPPath) = path / "active"
  protected def waitingUsers(path: HTTPPath) = path / "waiting"
  protected def doneUsers(path: HTTPPath) = path / "done"
  protected def okResponses(path: HTTPPath) = path / "ok"
  protected def koResponses(path: HTTPPath) = path / "ko"
  protected def allResponses(path: HTTPPath) = path / "all"
  protected def count(path: HTTPPath) = path / "count"
  protected def min(path: HTTPPath) = path / "min"
  protected def max(path: HTTPPath) = path / "max"
  protected def mean(path: HTTPPath) = path / "mean"
  protected def stdDev(path: HTTPPath) = path / "stdDev"
  protected def percentiles1(path: HTTPPath) = path / percentiles1Name
  protected def percentiles2(path: HTTPPath) = path / percentiles2Name
  protected def percentiles3(path: HTTPPath) = path / percentiles3Name
  protected def percentiles4(path: HTTPPath) = path / percentiles4Name
}
