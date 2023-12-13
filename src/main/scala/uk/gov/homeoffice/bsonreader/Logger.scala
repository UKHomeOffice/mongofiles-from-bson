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

//object IOLogger extends StrictLogging:
//
//  val ioLogger = Slf4jLogger.create[IO]
//
//  def info(msg :String) :IO[Unit] =
//    ioLogger.flatMap(_.info(msg))
//
//  // for use in for-yields
//  def infoAR(msg :String) :EitherT[IO, String, Unit] =
//    EitherT(ioLogger.flatMap(_.info(msg)).map { _ => Right(()) })
//
//  def error(msg :String) :IO[Unit] =
//    ioLogger.flatMap(_.error(msg))
//
//  def debug(msg :String) :IO[Unit] =
//    ioLogger.flatMap(_.debug(msg))
//
