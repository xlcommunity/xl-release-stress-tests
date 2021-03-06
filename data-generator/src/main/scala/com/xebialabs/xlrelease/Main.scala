package com.xebialabs.xlrelease

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory.parseResources
import com.typesafe.scalalogging.LazyLogging
import com.xebialabs.xlrelease.client.XlrClient
import com.xebialabs.xlrelease.generator.{SpecialDayGenerator, ReleasesGenerator}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure

object Main extends App with LazyLogging {

  val config = parseResources("data-generator.conf")
    .withFallback(ConfigFactory.load())

  private val completedReleasesAmount = config.getInt("xl.data-generator.completed-releases")
  private val activeReleasesAmount = config.getInt("xl.data-generator.active-releases")
  private val templatesAmount: Int = config.getInt("xl.data-generator.templates")

  logger.info("Active releases: {}", activeReleasesAmount.toString)
  logger.info("Completed releases: {}", completedReleasesAmount.toString)
  logger.info("Templates: {}", templatesAmount.toString)

  val client = new XlrClient(
    config.getString("xl.data-generator.server-url"),
    config.getString("xl.data-generator.username"),
    config.getString("xl.data-generator.password"))

  val importTemplateFuture = client.importTemplate("/many-automated-tasks.xlr")

  val specialDaysFuture = client.createCis(SpecialDayGenerator.generateSpecialDays())

  val releaseGenerator = new ReleasesGenerator()
  val dependantReleaseFuture = client.createCis(releaseGenerator.generateDependentRelease())
  val allReleasesFuture = dependantReleaseFuture.flatMap(_ => {
    // Creating some content to increase repository size
    val createTemplateReleasesFutures = releaseGenerator
      .generateTemplateReleases(templatesAmount)
      .map(client.createCis)
    val createActiveReleasesFutures = releaseGenerator
      .generateActiveReleases(activeReleasesAmount)
      .map(client.createCis)
    val createCompletedReleasesFutures = releaseGenerator
      .generateCompletedReleases(completedReleasesAmount)
      .map(client.createCis)

    Future.sequence(
      createTemplateReleasesFutures ++
         createActiveReleasesFutures ++
         createCompletedReleasesFutures)
  })

  val allResponses = Future.sequence(
    Seq(importTemplateFuture, allReleasesFuture, specialDaysFuture))

  allResponses.andThen {
    case Failure(ex) =>
      logger.error("Could not generate data set: ", ex)
  } andThen {
    case _ =>
      logger.debug("Shutting down the actor system after everything has been done.")
      client.system.shutdown()
      client.system.awaitTermination()
  }
}
