import sbt.Keys.libraryDependencies
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    
  )

  // These dependencies are attached to the dedicated `it` subproject in build.sbt,
  // so they do not need the deprecated `IntegrationTest` configuration suffix.
  val it: Seq[ModuleID] = Seq(
    "com.github.tomakehurst"   % "wiremock-jre8"              % "3.0.1",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "7.0.2"
  )
}
