package com.example

import akka.actor.{ActorSystem, Actor}

import akka.http.Http
import akka.http.model._
import StatusCodes._
import HttpMethods._
import akka.http.model.headers.HttpOriginRange.*
import akka.http.server.RouteResult.Rejected
import headers._
import akka.http.server._
import Directives._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.server.directives.AuthenticationDirectives.authenticateOrRejectWithChallenge
import spray.json.DefaultJsonProtocol
import akka.http.marshallers.xml.ScalaXmlSupport
import scala.concurrent.Future
import scala.xml._

import akka.stream.ActorFlowMaterializer
import com.typesafe.config.ConfigFactory
import grizzled.slf4j.Logging

case class ClientGreeting(greet: String)
case class ServerGreeting(greet: String)

trait GreetingProtocol extends DefaultJsonProtocol {
  implicit val cgFormat = jsonFormat1(ClientGreeting)
  implicit val sgFormat = jsonFormat1(ServerGreeting)
}


object MyService extends App with Router with Logging {
  val system = actorSystem

  val config = ConfigFactory.load()

  Http().bind(interface = "myhost.com", port = 8080).startHandlingWith(logRequestResult("akka-http-test"){myRoute})
}


trait Router extends Logging with ScalaXmlSupport with GreetingProtocol {
  implicit val actorSystem = ActorSystem()
  implicit val materializr = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val myUserPassAuthenticator: Option[BasicHttpCredentials] => Future[AuthenticationResult[Int]] =
    userPass => {
      val maybeUser = userPass flatMap { case BasicHttpCredentials(user, password) =>
        if (user == "me" && password == "me") Some(1) else None
      }
      val authResult = maybeUser match {
        case Some(user) => Right(user)
        case None       => Left(HttpChallenge("Basic", "realm"))
      }
      Future.successful(authResult)
    }

  /* as implemented in spray
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

  //TODO: add list of allowed hosts

  implicit val optionsRejectionHandler = RejectionHandler {
    case MethodRejection(_) :: _ => //expression doesn't match MethodRejection(OPTIONS)
      complete(HttpResponse(200).withHeaders(`Access-Control-Allow-Origin`(*),
        //`Access-Control-Allow-Methods`(OPTIONS, GET, POST, PUT, DELETE)
        `Access-Control-Allow-Credentials`(true))
      )
  }

  def addAccessControlHeaders = mapResponseHeaders { headers =>
    `Access-Control-Allow-Origin`(*/*HttpOriginRange(HttpOrigin("", Host("null")))*/) +: `Access-Control-Allow-Credentials`(true) +: headers
  }


  val myRoute =
      /*options { ctx => //should work for any route?
        logger.debug(s"pre-flight request for ${ctx.request.getUri()}")
        logger.debug("Sequence of rejections:")
        recoverRejectionsWith { rejections =>
          rejections.foreach(logger.debug(_))
          if (rejections.forall(_.isInstanceOf[MethodRejection]))
        }
      } ~*/
    addAccessControlHeaders {
      options {
        complete(HttpResponse(200).withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)
        )
        )
      } ~
      path("") {
        /*options {
          complete(HttpResponse(200).withHeaders(`Access-Control-Allow-Origin`(*),
            //`Access-Control-Allow-Methods`(OPTIONS, GET, POST, PUT, DELETE)
            `Access-Control-Allow-Credentials`(true))
          )
        } ~*/
        get {
          //respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
            complete {
              ToResponseMarshallable(OK ->
                <html>
                  <body>
                    <h1>Say hello to <i>akka-http</i>!</h1>
                  </body>
                </html>
              )
            }
          //}
        } ~
        (post | put) {
          complete {
            OK -> "hello"
          }
        } /*~
        options {
          complete(HttpResponse(200, headers = `Access-Control-Allow-Methods`(OPTIONS, POST, PUT) :: allowOriginHeader ::
            optionsCorsHeaders ))
        }*/
      } ~
      authenticateOrRejectWithChallenge(myUserPassAuthenticator).apply { user: Int =>
        path("greet") {
          (get & entity(as[ClientGreeting])) { greet =>
              logger.info(s"Client greeted us with '${greet.greet}'")
              complete {
                ToResponseMarshallable(OK -> ServerGreeting("Hello there!"))
              }
          }
        } ~
        path("cors") {
          (get | put) {
            complete(OK -> "Hello")
          }
        }
      }
    }
}
