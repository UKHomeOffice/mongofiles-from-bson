package uk.gov.homeoffice.bsonreader

import cats.effect.*
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.{*, given}
import java.util.Base64

case class Chunk(
  id :String,
  fileId :String,
  n :Int,
  data :String
) {
  def dataAsBinary() :Array[Byte] = Base64.getDecoder().decode(data)
}

object Chunk:

  def chunkFromJson(json :Json) :Either[String, Chunk] =
    //println(s"here with $json")
    (for {
      id <- json.hcursor.downField("_id").downField("$oid").as[String].toOption
      fileId <- json.hcursor.downField("files_id").downField("$oid").as[String].toOption
      n <- json.hcursor.downField("n").as[Int].toOption
      data <- json.hcursor.downField("data").downField("$binary").downField("base64").as[String].toOption
    } yield { Chunk(id, fileId, n, data) }) match {
      case Some(chunk) => Right(chunk)
      case None =>
        println(s"Missing element in structure: ${json.spaces4}")
        Left(s"Missing element in structure: ${json.spaces4}")
    }

  //def writeToDisk(chunk :Chunk) = IO {
  //  val filename = s"${chunk.fileId}.${chunk.n}"
  //  java.nio.file.Files.write(java.nio.file.Paths.get(filename), chunk.dataAsBinary())
  //}

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
    //println(s"fileIndex here with $json")
    (for {
      id <- json.hcursor.downField("_id").downField("$oid").as[String].toOption
      filename <- json.hcursor.downField("filename").as[String].toOption
      length <- json.hcursor.downField("length").as[Int].toOption
      chunkSize <- json.hcursor.downField("chunkSize").as[Int].toOption
    } yield { FileIndex(id, filename, length.toLong, chunkSize.toLong) }) match {
      case Some(fileIndex) =>
        //println(s"Right $fileIndex")
        Right(fileIndex)
      case None =>
        println(s"Missing element in structure: ${json.spaces4}")
        Left(s"Missing element in structure: ${json.spaces4}")
    }

