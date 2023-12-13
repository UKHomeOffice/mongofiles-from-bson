package uk.gov.homeoffice.bsonreader

import cats.effect.{IO, IOApp, Concurrent}
import fs2.io.file.{Files, Path}

import cats.data.*
import cats.effect.*
import cats.implicits.{*, given}
import cats.effect.implicits.{*, given}
import com.typesafe.config.*
import java.io.*
import cats.effect.unsafe.implicits.global

object MainApp extends IOApp:

  def uncompressor(stream :fs2.Stream[IO, Byte]) :fs2.Stream[IO, Byte] =
    stream.through(fs2.io.toInputStream)
      .flatMap { inputStream => fs2.io.readInputStream(IO(Ungzip.ungzip(inputStream)), 512) }

  def bsonStreamer(stream :fs2.Stream[IO, Byte]) :fs2.Stream[IO, Option[Either[String, io.circe.Json]]] =
    stream.through(fs2.io.toInputStream).flatMap { inputStream =>
      fs2.Stream.repeatEval { BsonReader.nextBsonObject(inputStream) }.noneTerminate
    }

  def bsonReader(filename :String) :fs2.Stream[IO, io.circe.Json] = {
    val stream = Files[IO].readAll(Path(filename))
    val uncompressedStream = uncompressor(stream)
    val bsonObjectStream = bsonStreamer(uncompressedStream)
    bsonObjectStream
      .collect { case Some(Right(json)) => json }
  }

  //def chunkReader(chunkFilename :String) :fs2.Stream[IO, Either[String, Chunk]] =
  // bsonReader(chunkFilename).map { json => Chunk.chunkFromJson(json) }

  def fileIndexReader(fileIndexFilename :String) :fs2.Stream[IO, Either[String, FileIndex]] = {
    bsonReader(fileIndexFilename)
      .map { json => FileIndex.fileIndexFromJson(json) }
      //.map {
      //  case Right(f) =>
      //      println(s"${f.filename} (chunks: ${f.chunkCount()})")
      //      Right(f)
      //  case Left(err) =>
      //    println(s"failed: $err")
      //    Left(err)
      //}
  }

  def extractFiles(appConfig :AppConfig) :IO[ExitCode] = {
    fileIndexReader(appConfig.fileIndexFilename).evalMap {
      case Right(file) if file.filename.matches(appConfig.fileMatchRegex) => 
        println(s"MATCHED: ${file.filename} (chunks: ${file.chunkCount()})")
        
        bsonReader(appConfig.chunksFilename)
          .map { json => Chunk.chunkFromJson(json) }
          //.evalTap {
          //  case Right(chunk) => IO(println(s"reading chunk: ${chunk.fileId}:${chunk.n}"))
          //  case x => IO(println(s"chunk streaming error: $x"))
          //}
          .zipWithIndex
          .evalTap {
            case (_, index) if index % 10000 == 0 => IO(println(s"Scanned $index chunks..."))
            case _ => IO(())
          }
          .collect {
            case (Right(chunk), _) if chunk.fileId == file.id =>
              println(s"Chunk ${chunk.n} located of ${file.filename}")

              // I happen to know all the files are only one chunk!
              // This is a big hacky shortcut, but one I can get away with.
              val filename = s"${chunk.fileId}.${chunk.n}"
              java.nio.file.Files.write(java.nio.file.Paths.get(filename), chunk.dataAsBinary())

              println(s"Written $filename to disk")
              Right(chunk)
          }
          .compile
          .drain
      case _ =>
        IO(())
    }
    .compile
    .drain
    .as(ExitCode(0))
  }

  def listFiles(appConfig: AppConfig) :IO[ExitCode] = {
    fileIndexReader(appConfig.fileIndexFilename).map {
      case Right(file) if file.filename.matches(appConfig.fileMatchRegex) => 
        println(s"MATCHED: ${file.filename} (chunks: ${file.chunkCount()})")
      case Right(file) => 
        //println(s"SKIPPED due to regex: ${file.filename}")
      case Left(x) =>
        println(s"ERROR: $x")
    }
    .compile
    .drain
    .map { _ => ExitCode(0) }
  }

  case class AppConfig(
    chunksFilename :String,
    fileIndexFilename :String,
    fileMatchRegex :String = ".*",
    writeDirectory :String = "/tmp"
  )

  // Usage:
  //
  // List all the files that start with CustomerReport
  //
  // java -jar bsonreader.json -c chunks.bson.gz -i fs.files.bson.gz --match "^CustomerReport.*" list
  //
  // Extract all the files that start with CustomerReport to /home/phill
  //
  // java -jar bsonreader.json -c chunks.bson.gz -i fs.files.bson.gz --match "^CustomerReport.*" -w /home/phill extract
  //

  def run(args: List[String]): IO[ExitCode] = {
    
    val argParser = new ArgParser(args)
    try {

      val config = AppConfig(
        chunksFilename = argParser.valueBehindOption("-c", "--chunks").orElse("/home/phill/projects/DPSPS-1358-FileRestore/handoff.chunks.bson.gz"),
        fileIndexFilename = argParser.valueBehindOption("-i", "--index").orElse("/home/phill/projects/DPSPS-1358-FileRestore/handoff.files.bson.gz"),
        fileMatchRegex = argParser.valueBehindOption("-m", "--match").orElse(".*"),
        writeDirectory = argParser.valueBehindOption("-w", "--write-dir").orElse("/tmp")
      )

      val commands = argParser.getCommands()
      (commands.contains("list"), commands.contains("extract")) match {
        case (true, false) => listFiles(config)
        case (false, true) => extractFiles(config)
        case (true, true) =>
          println("Ambiguous commands")
          IO(ExitCode(200))
        case (false, false) =>
          println("Missing commands")
          IO(ExitCode(200))
      }

    } catch {
      case ex :Exception =>
        println(s"Exception: $ex")
        IO(ExitCode(255))
    }

  }
    // java -jar bsonreader.jar -c chunks.bson.gz -f files.bson.gz list-files
    // java -jar bsonreader.jar -c chunks.bson.gz -f files.bson.gz extract-file *.jpg

