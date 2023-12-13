package uk.gov.homeoffice.bsonreader

import cats.effect.{IO, IOApp, Concurrent}
import fs2.io.file.{Files => FS2Files, Path}

import cats.data.*
import cats.effect.*
import cats.implicits.{*, given}
import cats.effect.implicits.{*, given}
import com.typesafe.config.*
import java.io.*
import java.nio.file.{Files => NioFiles, Paths}
import cats.effect.unsafe.implicits.global
import scopt.OParser

object MainApp extends IOApp:

  def uncompressor(stream :fs2.Stream[IO, Byte]) :fs2.Stream[IO, Byte] =
    stream.through(fs2.io.toInputStream)
      .flatMap { inputStream => fs2.io.readInputStream(IO(Ungzip.ungzip(inputStream)), 512) }

  def bsonStreamer(stream :fs2.Stream[IO, Byte]) :fs2.Stream[IO, Option[Either[String, io.circe.Json]]] =
    stream.through(fs2.io.toInputStream).flatMap { inputStream =>
      fs2.Stream.repeatEval { BsonReader.nextBsonObject(inputStream) }.noneTerminate
    }

  def bsonReader(filename :String) :fs2.Stream[IO, io.circe.Json] = {
    val stream = FS2Files[IO].readAll(Path(filename))
    val uncompressedStream = uncompressor(stream)
    val bsonObjectStream = bsonStreamer(uncompressedStream)
    bsonObjectStream
      .collect { case Some(Right(json)) => json }
  }

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
              NioFiles.write(Paths.get(filename), chunk.dataAsBinary())

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
    chunksFilename :String = "fs.files.bson.gz",
    fileIndexFilename :String = "fs.chunks.bson.gz",
    fileMatchRegex :String = ".*",
    writeDirectory :String = "/tmp",
    performExtract :Boolean = false
  )

  def run(args: List[String]): IO[ExitCode] = {
    val builder = OParser.builder[AppConfig]
    val parser1 = {
      import builder._
      OParser.sequence(
        programName("mongofiles-from-bson"),
        head("mongofiles-from-bson", "0.1"),
        // option -f, --foo
        opt[String]('c', "chunks")
          .action((x, c) => c.copy(chunksFilename = x))
          .text("location of the chunks.bson.gz file"),
        opt[String]('f', "files")
          .action((x, c) => c.copy(fileIndexFilename = x))
          .text("location of the files.bson.gz file"),
        opt[String]('w', "write-dir")
          .action((x, c) => c.copy(writeDirectory = x))
          .text("directory to extract files into when using the -x argument"),
        opt[String]('m', "match")
          .action((x, c) => c.copy(fileMatchRegex = x))
          .text("provide a regular expression to filter filenames by"),
        opt[Unit]('x', "extract")
          .action((x, c) => c.copy(performExtract = true))
          .text("extract and write matching files to disk"),
      )
    }

    OParser.parse(parser1, args, AppConfig()) match {
      case Some(appConfig) if !NioFiles.exists(Paths.get(appConfig.chunksFilename)) =>
        IO {
          println(s"chunks file not found. please provide -c <path> argument which is valid")
          ExitCode(255)
        }
      case Some(appConfig) if !NioFiles.exists(Paths.get(appConfig.fileIndexFilename)) =>
        IO {
          println(s"files file not found. please provide -f <path> argument which is valid")
          ExitCode(255)
        }
      case Some(appConfig) if !NioFiles.exists(Paths.get(appConfig.writeDirectory)) =>
        IO {
          println(s"write directory not found. please provide -w <path> argument which is valid")
          ExitCode(255)
        }
      case Some(appConfig) if !appConfig.performExtract =>
        println(s"Got this: $appConfig")
        listFiles(appConfig)
      case Some(appConfig) if appConfig.performExtract =>
        extractFiles(appConfig)
      case _ =>
        // arguments are bad, error message will have been displayed
        IO {
          println("Exiting due to bad arguments")
          ExitCode(255)
        }
    }
  }
