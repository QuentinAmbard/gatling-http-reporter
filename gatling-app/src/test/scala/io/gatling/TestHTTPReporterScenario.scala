package io.gatling

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef

//import scala.concurrent.duration._


class TestHTTPReporterScenario extends Simulation {

  val scn = scenario("BasicSimulation").exec(Predef.http("Retrieve home page 1").get("http://localhost:8080/api/timeout"))

//  val scn2 = scenario("scenario computer")
//    .exec(Predef.http("Retrieve computer").get("http://myprocurement.fr"))

  setUp(
    scn.inject(constantUsersPerSec(1) during 30)
//    scn.inject(rampUsers(100) during  (10 seconds), constantUsersPerSec(100) during 60),
//      scn2.inject(rampUsers(10) during  (10 seconds), constantUsersPerSec(10) during 60)
  ).protocols(Predef.http)
}
