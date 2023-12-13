package uk.gov.homeoffice.bsonreader

import com.typesafe.scalalogging.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Logger extends StrictLogging:

  def info(msg :String) :Unit =
    logger.info(msg)

  def error(msg :String) :Unit =
    logger.error(msg)

  def debug(msg :String) :Unit =
    logger.debug(msg)

