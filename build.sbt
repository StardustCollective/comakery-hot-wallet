name := "comakery-hot-wallet"

version := "0.0.1-rc1"

scalaVersion := "2.12.10"

assemblyMergeStrategy in assembly := {
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("cats", "effect", xs @ _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "logback.xml" => MergeStrategy.first // picks the logback from this project
  case x if x.contains("org/constellation/keytool/BuildInfo$.class") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := "cl-comakery-hot-wallet.jar"

lazy val versions = new {
  val http4s = "0.21.2"
  val circe = "0.13.0"
  val constellation = "2.13.8"
  val scopt = "4.0.0-RC2"
  val pureconfig = "0.12.3"
  val scalaTest = "3.0.8"
  val cats = "2.1.1"
}

val testDependencies = Seq(
  "org.scalatest"            %% "scalatest"           % versions.scalaTest
).map(_ % "test")

libraryDependencies ++= Seq(
  "org.http4s"               %% "http4s-blaze-client" % versions.http4s,
  "org.http4s"               %% "http4s-circe"        % versions.http4s,
  "org.typelevel"            %% "cats-core"           % versions.cats,
  "org.typelevel"            %% "cats-effect"         % versions.cats,
  "io.circe"                 %% "circe-core"          % versions.circe,
  "io.circe"                 %% "circe-generic"       % versions.circe,
  "com.github.scopt"         %% "scopt"               % versions.scopt,
  "com.github.pureconfig"    %% "pureconfig"          % versions.pureconfig,
  "org.constellation"        %% "cl-wallet"           % versions.constellation from s"https://github.com/Constellation-Labs/constellation/releases/download/v${versions.constellation}/cl-wallet.jar",
  "org.constellation"        %% "cl-keytool"          % versions.constellation from s"https://github.com/Constellation-Labs/constellation/releases/download/v${versions.constellation}/cl-keytool.jar"
) ++ testDependencies
