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

package io.gatling.core.check.extractor.string

import io.gatling.commons.validation._
import io.gatling.core.check._
import io.gatling.core.check.extractor._
import io.gatling.core.session._

trait BodyStringCheckType

object BodyStringCheckBuilder {

  val BodyString: FindCheckBuilder[BodyStringCheckType, String, String] = {

    val extractor = new Extractor[String, String] with SingleArity {
      override val name: String = "bodyString"
      override def apply(prepared: String): Validation[Some[String]] = Some(prepared).success
    }.expressionSuccess

    new DefaultFindCheckBuilder[BodyStringCheckType, String, String](extractor, displayActualValue = false)
  }
}