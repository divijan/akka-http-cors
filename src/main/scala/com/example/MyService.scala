package com.example

import akka.actor.{ActorSystem, Actor}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import StatusCodes._
import HttpMethods._
import akka.stream.scaladsl.Sink
import headers._
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateOrRejectWithChallenge
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import scala.concurrent.Future
import scala.io.StdIn
import scala.xml._

import akka.stream.ActorMaterializer
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
  implicit val fm = materializr

  //All this stuff is needed because of the bug in bindAndstartHandlingWith method in M4:
  //https://groups.google.com/forum/#!msg/akka-user/n1y6NLDP_f8/LtNnMxzs0RoJ
  val server = Http().bind("localhost", 8080)
  val future = server.to(Sink.foreach { conn =>
    println(s"connection from ${conn.remoteAddress}")
    conn.flow.join(logRequestResult("akka-http-test"){myRoute}).run()
  }).run()
  future.flatMap(s => {
    println(s"bound to ${s.localAddress}")
    StdIn.readLine()
    s.unbind()
  }).map(_ => system.shutdown())
    .recover{
    case e: Exception =>
      e.printStackTrace()
      system.shutdown()
  }
}


trait Router extends Logging with ScalaXmlSupport with GreetingProtocol with CorsSupport {
  implicit val actorSystem = ActorSystem()
  implicit val materializr = ActorMaterializer()
  implicit val dispatcher = actorSystem.dispatcher
  private val challenge = HttpChallenge("Basic", "realm")

  val myUserPassAuthenticator: Option[BasicHttpCredentials] => Future[AuthenticationResult[Int]] =
    userPass => {
      val maybeUser = userPass flatMap { case BasicHttpCredentials(user, password) =>
        if (user == "me" && password == "me") Some(1) else None
      }
      val authResult = maybeUser match {
        case Some(user) => Right(user)
        case None       => Left(challenge)
      }
      Future.successful(authResult)
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
    corsHandler {
      path("") {
        get {
          complete {
            ToResponseMarshallable(OK ->
              <html>
                <body>
                  <h1>Say hello to <i>akka-http</i>!</h1>
                </body>
              </html>
            )
          }
        } ~
        (post | put) {
          complete {
            OK -> "hello"
          }
        }
      } ~
      //the next two directives look sort of like our authserver
      authenticateOrRejectWithChallenge(myUserPassAuthenticator).apply { user: Int =>
        path("cors") {
          (get | put) {
            complete(OK -> "Hello")
          }
        }
      } ~
      path("greet") {
        authenticateOrRejectWithChallenge(myUserPassAuthenticator).apply { user: Int =>
          (get & entity(as[ClientGreeting])) { greet =>
            logger.info(s"Client greeted us with '${greet.greet}'")
            complete {
              ToResponseMarshallable(OK -> ServerGreeting("Hello there!"))
            }
          }
        }
      }
    }
}
