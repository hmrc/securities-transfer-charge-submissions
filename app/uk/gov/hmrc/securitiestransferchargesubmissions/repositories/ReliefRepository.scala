/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.securitiestransferchargesubmissions.repositories

import org.mongodb.scala.model.{Filters, ReplaceOptions}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** The reliefs last fetched from ETMP. There is only ever one of these. */
final case class CachedReliefs(reliefs: List[Relief], fetchedAt: Instant, _id: String = CachedReliefs.documentId)

object CachedReliefs:
  val documentId = "reliefs"

  given Format[CachedReliefs] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    Json.format[CachedReliefs]

trait ReliefRepository:
  def find(): Future[Option[CachedReliefs]]
  def save(cached: CachedReliefs): Future[Unit]

// No TTL index on purpose: expired data is kept so it can still be served if ETMP is unavailable.
@Singleton
class ReliefRepositoryImpl @Inject() (
  mongoComponent: MongoComponent
)(using ec: ExecutionContext)
    extends PlayMongoRepository[CachedReliefs](
      collectionName = "reliefs",
      mongoComponent = mongoComponent,
      domainFormat   = CachedReliefs.given_Format_CachedReliefs,
      indexes        = Seq.empty
    )
    with ReliefRepository:

  override def find(): Future[Option[CachedReliefs]] =
    collection.find(Filters.equal("_id", CachedReliefs.documentId)).headOption()

  override def save(cached: CachedReliefs): Future[Unit] =
    collection
      .replaceOne(Filters.equal("_id", CachedReliefs.documentId), cached, ReplaceOptions().upsert(true))
      .toFuture()
      .map(_ => ())
