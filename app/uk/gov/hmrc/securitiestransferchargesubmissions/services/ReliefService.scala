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

package uk.gov.hmrc.securitiestransferchargesubmissions.services

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.ReliefClient
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief
import uk.gov.hmrc.securitiestransferchargesubmissions.repositories.{CachedReliefs, ReliefRepository}

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** Supplies the list of reliefs, cached in Mongo for `appConfig.reliefsCacheTtl` (a day by default).
  *
  * If the cache has expired and ETMP cannot be reached, the expired data is returned rather than failing.
  * Only when there is nothing cached at all does a failure from ETMP reach the caller.
  */
@Singleton
class ReliefService @Inject() (
  client: ReliefClient,
  repository: ReliefRepository,
  appConfig: AppConfig,
  clock: Clock
)(using ec: ExecutionContext)
    extends Logging:

  def getAllReliefs()(using HeaderCarrier): Future[List[Relief]] =
    readCache().flatMap {
      case Some(cached) if isFresh(cached) => Future.successful(cached.reliefs)
      case existing                        => refresh(existing)
    }

  private def isFresh(cached: CachedReliefs): Boolean =
    cached.fetchedAt.plus(appConfig.reliefsCacheTtl).isAfter(Instant.now(clock))

  private def refresh(existing: Option[CachedReliefs])(using HeaderCarrier): Future[List[Relief]] =
    client.getReliefs().flatMap { reliefs =>
      repository
        .save(CachedReliefs(reliefs, Instant.now(clock)))
        .recover { case e => logger.warn("Could not save reliefs to the cache", e) }
        .map(_ => reliefs)
    }.recoverWith { case e =>
      existing match
        case Some(stale) =>
          logger.warn(s"ETMP reliefs lookup failed, serving stale reliefs fetched at ${stale.fetchedAt}", e)
          Future.successful(stale.reliefs)
        case None =>
          Future.failed(e)
    }

  // A broken cache should not stop us going to ETMP
  private def readCache(): Future[Option[CachedReliefs]] =
    repository.find().recover { case e =>
      logger.warn("Could not read reliefs from the cache", e)
      None
    }
