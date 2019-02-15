package io.gatling

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef

class TestHTTPReporterScenario extends Simulation {

  val scn = scenario("BasicSimulation")
    .exec(Predef.http("Retrieve home page 1").get("http://computer-database.gatling.io"))

  val scn2 = scenario("scenario computer")
    .exec(Predef.http("Retrieve computer").get("http://computer-database.gatling.io/computers/381"))

  setUp(
    scn.inject(constantUsersPerSec(5) during 20),
      scn2.inject(constantUsersPerSec(5) during 20)
  ).protocols(Predef.http)
}
