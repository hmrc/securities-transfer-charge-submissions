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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{ReliefClient, ReliefClientException}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief
import uk.gov.hmrc.securitiestransferchargesubmissions.repositories.{CachedReliefs, ReliefRepository}

import java.time.{Clock, Duration, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReliefServiceSpec extends SpecBase:

  private given HeaderCarrier = HeaderCarrier()

  private val now   = Instant.parse("2026-03-31T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneId.of("UTC"))

  private val cachedReliefs = List(Relief("Cached Relief", 10))
  private val etmpReliefs   = List(Relief("ETMP Relief", 20))

  private val client     = mock[ReliefClient]
  private val repository = mock[ReliefRepository]

  private val service = new ReliefService(client, repository, appConfig, clock)

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(client, repository)
    when(repository.save(any())).thenReturn(Future.successful(()))

  private def cacheFetchedAt(instant: Instant): Unit =
    when(repository.find()).thenReturn(Future.successful(Some(CachedReliefs(cachedReliefs, instant))))

  "ReliefService.getAllReliefs" should {

    "return the cached reliefs without calling ETMP when they are less than a day old" in {
      cacheFetchedAt(now.minus(Duration.ofHours(23)))

      service.getAllReliefs().futureValue shouldBe cachedReliefs

      verify(client, never()).getReliefs()(using any())
    }

    "call ETMP and save the result when nothing is cached" in {
      when(repository.find()).thenReturn(Future.successful(None))
      when(client.getReliefs()(using any())).thenReturn(Future.successful(etmpReliefs))

      service.getAllReliefs().futureValue shouldBe etmpReliefs

      verify(repository).save(CachedReliefs(etmpReliefs, now))
    }

    "call ETMP and save the result when the cache is more than a day old" in {
      cacheFetchedAt(now.minus(Duration.ofHours(25)))
      when(client.getReliefs()(using any())).thenReturn(Future.successful(etmpReliefs))

      service.getAllReliefs().futureValue shouldBe etmpReliefs

      verify(repository).save(CachedReliefs(etmpReliefs, now))
    }

    "return the stale reliefs when the cache has expired and ETMP fails" in {
      cacheFetchedAt(now.minus(Duration.ofDays(3)))
      when(client.getReliefs()(using any())).thenReturn(Future.failed(ReliefClientException("ETMP down")))

      service.getAllReliefs().futureValue shouldBe cachedReliefs

      verify(repository, never()).save(any())
    }

    "fail when nothing is cached and ETMP fails" in {
      when(repository.find()).thenReturn(Future.successful(None))
      when(client.getReliefs()(using any())).thenReturn(Future.failed(ReliefClientException("ETMP down")))

      service.getAllReliefs().failed.futureValue shouldBe a[ReliefClientException]
    }

    "still return the ETMP reliefs when saving to the cache fails" in {
      when(repository.find()).thenReturn(Future.successful(None))
      when(client.getReliefs()(using any())).thenReturn(Future.successful(etmpReliefs))
      when(repository.save(any())).thenReturn(Future.failed(new RuntimeException("mongo down")))

      service.getAllReliefs().futureValue shouldBe etmpReliefs
    }

    "go to ETMP when reading the cache fails" in {
      when(repository.find()).thenReturn(Future.failed(new RuntimeException("mongo down")))
      when(client.getReliefs()(using any())).thenReturn(Future.successful(etmpReliefs))

      service.getAllReliefs().futureValue shouldBe etmpReliefs
    }

    "honour a configured cache TTL" in {
      val shortTtlConfig = appConfigWithOverrides("reliefs.cache-ttl = 1 hour")
      val shortTtlService = new ReliefService(client, repository, shortTtlConfig, clock)
      cacheFetchedAt(now.minus(Duration.ofHours(2)))
      when(client.getReliefs()(using any())).thenReturn(Future.successful(etmpReliefs))

      shortTtlService.getAllReliefs().futureValue shouldBe etmpReliefs
    }
  }
