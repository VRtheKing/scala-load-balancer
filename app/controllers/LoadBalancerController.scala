package controllers

import play.api.libs.json.{JsValue, Json}

import javax.inject._
import play.api.mvc._
import play.api.libs.ws._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoadBalancerController @Inject()(
                                        cc: ControllerComponents,
                                        ws: WSClient
                                      )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

//  private val backends = Seq(
//    "http://localhost:9001",
//    "http://localhost:9002",
//    "http://localhost:9003"
//  )
  private val backends = Seq(
    "http://localhost:9000"
  )
  private var counter = 0

  private def nextBackend(): String = {
    val index = (counter+1) % backends.size
    counter += 1
    backends(index)
  }

  def proxy(path: String) = Action.async { request =>
    val backendUrl = s"${nextBackend()}/$path"
    val wsReq = ws.url(backendUrl).withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
    val authToken = request.headers.get("Authorization").getOrElse("")

    val futureResult: Future[Result] = request.method match {

      case "GET" =>
        wsReq.get().map { response =>
          val jsonBody: JsValue = Json.parse(response.body)
          Status(response.status)(jsonBody)
            .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
        }

      case "DELETE" =>
        wsReq.delete().map { response =>
          val jsonBody: JsValue = Json.parse(response.body)
          print(jsonBody)
          Status(response.status)(jsonBody)
            .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
        }

      case "POST"=>
        request.body.asJson match {
          case Some(json) =>
            wsReq.withHttpHeaders("Content-type" -> "application/json", "Authorization" -> authToken)
              .post(json)
              .map { response =>
//                print(response.headers.toSeq)
                val jsonBody: JsValue = Json.parse(response.body)
                Status(response.status)(jsonBody)
                  .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
              }
          case None =>
            Future.successful(BadRequest("Expected JSON body"))
        }
      case "PATCH"=>
        request.body.asJson match {
          case Some(json) =>
            wsReq.withHttpHeaders("Content-type" -> "application/json", "Authorization" -> authToken)
              .patch(json)
              .map { response =>
                //                print(response.headers.toSeq)
                val jsonBody: JsValue = Json.parse(response.body)
                Status(response.status)(jsonBody)
                  .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
              }
          case None =>
            Future.successful(BadRequest("Expected JSON body"))
        }

      case _ =>
        Future.successful(BadRequest("Unsupported Method"))
    }
    futureResult
  }
}
