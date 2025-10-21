package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.ws._
import services.LoadBalancerService
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoadBalancerController @Inject()(
                                        cc: ControllerComponents,
                                        lbService: LoadBalancerService,
                                        ws: WSClient
                                      )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val backends = Seq(
    "http://localhost:9001",
    "http://localhost:9002",
    "http://localhost:9003"
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

    val futureResult: Future[Result] = request.method match {

      case "GET" =>
        wsReq.get().map { response =>
          Status(response.status)(response.body)
            .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
        }

      case "DELETE" =>
        wsReq.delete().map { response =>
          Status(response.status)(response.body)
            .withHeaders(response.headers.map { case (k, v) => k -> v.mkString(",") }.toSeq: _*)
        }

      case "POST" | "PUT" =>
        request.body.asJson match {
          case Some(json) =>
            wsReq.withHttpHeaders("Content-Type" -> "application/json")
              .post(json)
              .map { response =>
                Status(response.status)(response.body)
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
