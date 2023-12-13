package uk.gov.homeoffice.bsonreader

import java.io.*
import com.mongodb.*
import org.bson.*
import org.bson.conversions.*
import org.bson.json.*
import scala.util.*
import _root_.io.circe.*
import _root_.io.circe.parser.*
import cats.effect.*

object BsonReader:
  val bd = new BasicBSONDecoder()

  def nextBsonObject(inputStream :InputStream) :IO[Either[String, Json]] = IO {
    for {
      bsonObject <- isToBson(inputStream)
      jsonString <- toJson(bsonObject)
    } yield { jsonString }
  }

  private def isToBson(is :InputStream): Either[String, BSONObject] = {
    Try(bd.readObject(is)).toEither
      .left.map { case ex => ex.getMessage }
  }

  private def toJson(bsonObject :BSONObject) :Either[String, Json] = {
    val jsonString = BasicDBObject(bsonObject.toMap).toJson
    parse(jsonString).left.map { case ex => ex.getMessage }
  }
    


