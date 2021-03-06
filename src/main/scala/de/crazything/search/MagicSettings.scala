package de.crazything.search

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{Duration, FiniteDuration}

//TODO: configuration and renaming
trait MagicSettings {
  val MAGIC_NUM_DEFAULT_HITS = 100
  val MAGIC_NUM_DEFAULT_HITS_FILTERED = 500
  val MAGIC_ONE_DAY: FiniteDuration = Duration.create(1, TimeUnit.DAYS)

  val DEFAULT_MILLIS_TO_CLOSE_OLD_READER = 1000

  val DEFAULT_DIRECTORY_NAME = "DEFAULT_DIRECTORY"
}
