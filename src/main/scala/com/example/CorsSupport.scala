package com.example

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{Route, Directive0, RejectionHandler, AuthenticationFailedRejection}
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory

trait CorsSupport {
  lazy val allowedOrigin = {
    val config = ConfigFactory.load()
    val sAllowedOrigin = config.getString("cors.allowed-origin")
    HttpOrigin(sAllowedOrigin)
  }
  //this rejection handler adds access control headers to Authentication Required response
  implicit val unauthRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case AuthenticationFailedRejection(_, challenge) =>
        complete(HttpResponse(401).withHeaders(
          `Access-Control-Allow-Origin`(allowedOrigin),
          `Access-Control-Allow-Credentials`(true),
          `WWW-Authenticate`(challenge)
        )
        )
    }
    .result()

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders: Directive0 = {
    mapResponseHeaders { headers =>
      `Access-Control-Allow-Origin`(allowedOrigin) +:
        `Access-Control-Allow-Credentials`(true) +:
        headers
    }
  }

  //this handles preflight OPTIONS requests. TODO: see if can be done with rejection handler,
  //otherwise has to be under addAccessControlHeaders
  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(200).withHeaders(
      `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)
    )
    )
  }

  def corsHandler(r: Route) = addAccessControlHeaders {
    preflightRequestHandler ~ r
  }
}

/* spray version

  trait CORSSupport {
    //this: HttpService =>
    private val allowOriginHeader = `Access-Control-Allow-Origin`(*)
    private val optionsCorsHeaders = List(
      `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
      `Access-Control-Max-Age`(1728000))

    implicit val myRejectionHandler = RejectionHandler {
      case MethodRejection(cookieName) :: _ =>
        complete(HttpResponse(BadRequest, entity = "No cookies, no service!!!"))
    }

    def cors[T]: Directive0 = mapRequestContext { ctx => ctx.withRouteResponseHandling({
      //It is an option request for a resource that responds to some other method
      case Rejected(x) if (ctx.request.method.equals(HttpMethods.OPTIONS) && x.exists(_.isInstanceOf[MethodRejection])) => {
        val allowedMethods: List[HttpMethod] = x.collect { case rejection: MethodRejection => rejection.supported }
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods :_*) :: allowOriginHeader ::
            optionsCorsHeaders
        ))
      }
    }).withHttpResponseHeadersMapped { headers =>
      allowOriginHeader :: headers
    }
    }
  }*/
