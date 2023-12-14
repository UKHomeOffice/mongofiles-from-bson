package uk.gov.homeoffice.bsonreader

import cats.effect.{IO, IOApp, Concurrent}
import fs2.io.file.{Files => FS2Files, Path}

import java.util.zip.GZIPInputStream
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

  def bsonReader(filename :String) :fs2.Stream[IO, io.circe.Json] = {
    val inputStream = new BufferedInputStream(GZIPInputStream(new BufferedInputStream(new FileInputStream(filename))))
    fs2.Stream.repeatEval { BsonReader.nextBsonObject(inputStream) }.unNoneTerminate
      .collect { case Right(json) => json }
  }

  // If the file list is too big, we can optimise here
  // by pulling files out of the fly, but it can mean
  // repetative passes over the chunks table.
  def cachedFileIndex(appConfig :AppConfig) :IO[List[FileIndex]] = {
    bsonReader(appConfig.fileIndexFilename)
      .map { json => FileIndex.fileIndexFromJson(json) }
      .evalTap {
        case Right(file) => IO(())
        case err => IO(println(s"Filtering out an error in the file index stream: $err"))
      }
      .collect { case Right(file) if file.filename.matches(appConfig.fileMatchRegex) => file }
      .compile
      .toList
  }

  def extractFiles(appConfig :AppConfig) :IO[ExitCode] = {
    cachedFileIndex(appConfig).flatMap { fileIndexList =>

      // Start reading entire chunk stream in a single pass
      // matching to any fileIndexList, for performance
      bsonReader(appConfig.chunksFilename)
        .map { json => Chunk.chunkFromJson(json) }
        .zipWithIndex
        .evalTap {
          case (_, idx) if idx % 5000 == 0 => IO(println(s"chunks scanned $idx..."))
          case _ => IO(())
        }
        .collect { case (Right(chunk), _) if fileIndexList.map(_.id).contains(chunk.fileId) =>
          val file = fileIndexList.find(_.id == chunk.fileId).get // shouldn't fail given the check above
          (file, chunk)
        }
        .map { case (file, chunk) => Chunk.writeNewChunkToDisk(file, chunk, appConfig.writeDirectory) }
        .compile
        .drain
        .as(ExitCode(0))
    }
  }

  def listFiles(appConfig: AppConfig) :IO[ExitCode] = {
    cachedFileIndex(appConfig).map { listOfFiles =>
      listOfFiles.collect { case file if file.filename.matches(appConfig.fileMatchRegex) => 
        println(s"MATCHED: ${file.filename} (chunks: ${file.chunkCount()})")
        file
      }
    }
    .as(ExitCode(0))
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
        listFiles(appConfig)
      case Some(appConfig) if appConfig.performExtract =>
        extractFiles(appConfig)
      case _ =>
        IO {
          println("Exiting due to bad arguments")
          ExitCode(255)
        }
    }
  }
