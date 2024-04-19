import Dependencies._

name := "socrata-http-common"

libraryDependencies ++= Seq(
  jakartaActivation,
  commonsCodec,
  commonsLang,
  jodaConvert,
  jodaTime,
  slf4jApi,
  simpleArm,
  rojomaJsonV3,
  rojomaJsonJackson,
  jackson,
  scalaTest % "test",
  scalaCheck % "test"
)
