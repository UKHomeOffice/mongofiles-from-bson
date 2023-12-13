package uk.gov.homeoffice.bsonreader

import java.io.*
import java.util.zip.GZIPInputStream
import scala.util.Try

object Ungzip:

  def ungzip(gzippedStream :InputStream) :InputStream = {
    Try(GZIPInputStream(gzippedStream)).toEither match {
      case Left(error) =>
        Logger.error(s"GZIP error: $error")
        throw new Exception(s"GZIP Broken Pipe: $error")
      case Right(uncompressedStream) =>
        uncompressedStream
    }
  }

