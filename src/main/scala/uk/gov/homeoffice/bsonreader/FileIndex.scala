package uk.gov.homeoffice.bsonreader

import cats.effect.*
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.{*, given}
import java.util.Base64
import java.nio.file.*
import scala.util.Try
import java.io.*

case class FileIndex(
  id :String,
  filename :String,
  length: Long,
  chunkSize :Long
) {
  def chunkCount() :Int = Math.ceil(length.toDouble / chunkSize.toDouble).toInt
}

object FileIndex:

  def fileIndexFromJson(json :Json) :Either[String, FileIndex] = 
    (for {
      id <- json.hcursor.downField("_id").downField("$oid").as[String].toOption
      filename <- json.hcursor.downField("filename").as[String].toOption
      length <- json.hcursor.downField("length").as[Int].toOption
      chunkSize <- json.hcursor.downField("chunkSize").as[Int].toOption
    } yield {
      /* TODO: we aren't honouring, keeping paths as encoded in gridfs.
       * All files treated according to their basename, meaning behaviours
       * for matching, duplicates are not ideal */
      val basename = Paths.get(filename).getFileName()
      FileIndex(id, basename.toString, length.toLong, chunkSize.toLong)
    }) match {
      case Some(fileIndex) =>
        Right(fileIndex)
      case None =>
        println(s"Missing element in structure: ${json.spaces4}")
        Left(s"Missing element in structure: ${json.spaces4}")
    }

