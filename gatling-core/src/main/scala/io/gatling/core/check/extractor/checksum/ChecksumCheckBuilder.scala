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

package io.gatling.core.check.extractor.checksum

import io.gatling.core.check.extractor.{ Extractor, SingleArity }
import io.gatling.commons.validation._
import io.gatling.core.check.{ DefaultFindCheckBuilder, FindCheckBuilder }
import io.gatling.core.session._

trait Md5CheckType
trait Sha1CheckType

object ChecksumCheckBuilder {

  private def checksum[T](algorithm: String): DefaultFindCheckBuilder[T, String, String] = {
    val extractor = new Extractor[String, String] with SingleArity {
      override val name: String = algorithm.toLowerCase
      override def apply(prepared: String): Validation[Some[String]] = Some(prepared).success
    }.expressionSuccess

    new DefaultFindCheckBuilder[T, String, String](extractor, displayActualValue = false)
  }

  val Md5: FindCheckBuilder[Md5CheckType, String, String] = checksum[Md5CheckType]("MD5")

  val Sha1: FindCheckBuilder[Sha1CheckType, String, String] = checksum[Sha1CheckType]("SHA1")
}