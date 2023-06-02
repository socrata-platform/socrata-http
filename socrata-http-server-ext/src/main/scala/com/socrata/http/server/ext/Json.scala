package com.socrata.http.server.ext

import com.rojoma.json.v3.codec.{JsonEncode, JsonDecode}
import com.rojoma.json.v3.io.JsonReaderException
import com.rojoma.json.v3.util.JsonUtil

import com.socrata.http.server.{HttpRequest, HttpResponse, responses}

case class Json[T](value: T)

object Json {
  implicit def intoResponse[T : JsonEncode]: IntoResponse[Json[T]] =
    new IntoResponse[Json[T]] {
      def intoResponse(r: Json[T]): HttpResponse = {
        responses.Json(r.value)
      }
    }

  implicit def fromRequest[T : JsonDecode]: FromRequest[Json[T]] =
    new FromRequest[Json[T]] {
      def extract(req: HttpRequest): HandlerDecision[Json[T]] = {
        req.reader match {
          case Right(reader) =>
            try {
              JsonUtil.readJson[T](reader) match {
                case Right(v) => Accepted(Json(v))
                case Left(e) => Rejected(???)
              }
            } catch {
              case e: JsonReaderException =>
                Rejected(???)
            }
          case Left(_) =>
            Rejected(???)
        }
      }
    }
}
