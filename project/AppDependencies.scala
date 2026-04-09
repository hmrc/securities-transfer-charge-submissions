import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "10.1.0"


  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    
  )

  val it = Seq(
    "com.github.tomakehurst"   % "wiremock-jre8"              % "3.0.1"                     % IntegrationTest,
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "7.0.1"                     % IntegrationTest
  )
}
