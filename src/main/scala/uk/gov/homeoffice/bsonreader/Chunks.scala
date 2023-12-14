package uk.gov.homeoffice.bsonreader

import cats.effect.*
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.{*, given}
import java.util.Base64
import java.nio.file.*
import scala.util.Try
import java.io.*

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

  def writeNewChunkToDisk(fileIndex :FileIndex, chunk :Chunk, writeDirectory :String) :Either[String, Unit] = Try {
    // If this the only chunk? If so, you can extract the chunk in one go!
    if (fileIndex.chunkCount() == 1 && chunk.n == 0) {
      val filename = Paths.get(writeDirectory, fileIndex.filename)
      Files.write(filename, chunk.dataAsBinary())
      println(s"Found chunk ${chunk.fileId}:${chunk.n}. Writing to $filename. File extraction completed")
    } else {
      maybeCompleteFile(fileIndex, chunk, writeDirectory) match {
        case Right(true) => println(s"FILE EXTRACTION COMPLETE: ${fileIndex.filename}")
        case Right(false) => ()
        case Left(err) =>
          throw new Exception(s"Unable to complete file ${fileIndex.filename}: $err")
          println(s"Unable to complete file ${fileIndex.filename}: $err")
      }
    }
  }.toEither.left.map { case ex :Exception => ex.getMessage }

  // TODO: Lots of room for improvement here!
  // 1. catch file read/write errors
  // 2. avoid readAllBytes and adopt streams to help in low memory environment
  def maybeCompleteFile(fileIndex :FileIndex, chunk :Chunk, writeDirectory :String) :Either[String, Boolean] = {
    def isChunkAvailable(n :Int) :Boolean = {
      val filename = Paths.get(writeDirectory, s"${fileIndex.filename}.$n")
      (chunk.n == n || Files.exists(filename))
    }

    def getChunkStream(n :Int) :Array[Byte] = {
      val filename = Paths.get(writeDirectory, s"${fileIndex.filename}.$n")
      if (chunk.n == n)
        chunk.dataAsBinary()
      else
        new FileInputStream(filename.toString).readAllBytes()
    }

    val chunksRequired = (0 until fileIndex.chunkCount()).toList
    val allChunksProvided = chunksRequired.forall(isChunkAvailable)
    allChunksProvided match {
      case true =>
        val filename = Paths.get(writeDirectory, s"${fileIndex.filename}")
        println(s"All chunks of $filename found. Restoring target file...")

        val fos = new FileOutputStream(filename.toString)
        chunksRequired.foreach { n =>
          fos.write(getChunkStream(n))
        }
        fos.close()

        Right(true)
      case false =>
        println(s"Chunk ${chunk.n} of ${fileIndex.filename} written to disk. Waiting on remaining chunks: ${chunksRequired.filterNot(isChunkAvailable).mkString(",")}")
        val filename = Paths.get(writeDirectory, s"${fileIndex.filename}.${chunk.n}")
        println(s"Writing to $filename (${fileIndex.filename}, $writeDirectory)")
        val fos = new FileOutputStream(filename.toString)
        fos.write(chunk.dataAsBinary())
        fos.close()
        Right(false)
    }
  }

