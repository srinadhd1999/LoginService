package controllers

import javax.inject._
import models.{User, UserRepository}
import play.api.libs.json._
import play.api.mvc._
import services.{KafkaMessageProducer, KafkaMessageConsumer}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class LoginController @Inject()(userRepository: UserRepository, cc: ControllerComponents, kafkaMessageProducer: KafkaMessageProducer, kafkaMessageConsumer: KafkaMessageConsumer)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  implicit val userFormat: Format[User] = Json.format[User]

  kafkaMessageConsumer.receiveMessages()

  def index: Action[AnyContent] =  Action { implicit request =>
    Ok(views.html.index())
  }

  def getUsers: Action[AnyContent] = Action.async {
    userRepository.list().map { user =>
      Ok(Json.toJson(user))
    }
  }

  def afterLogin: Action[AnyContent] = Action {
    Ok("http://localhost:9001/")
  }

  def afterLoginAdmin: Action[AnyContent] = Action {
    Ok(views.html.afterLoginAdmin())
  }

  def afterLoginUser: Action[AnyContent] = Action {
    Ok(views.html.afterLoginUser())
  }

  def loginCheck: Action[AnyContent] = Action.async { implicit request =>
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val emailValue = formData.get("email").flatMap(_.headOption)
    val passwordValue = formData.get("password").flatMap(_.headOption)
    (emailValue, passwordValue) match {
      case (Some(email), Some(password)) =>
        userRepository.authenticate(email, password).flatMap {
          case Some(user) =>
            if(email == "admin" && password == "admin") {
              kafkaMessageProducer.sendMessage("admin", "sample message to read")
              Future.successful(Redirect(s"http://localhost:9001/getProductList?user=admin").withSession("user" -> "admin").flashing("success" -> "User authenticated successfully!"))
            }
            else {
              val name = userRepository.getUserName(email)
              name.flatMap { userName =>
                println(s"User authenticated: $userName")
                kafkaMessageProducer.sendMessage(userName, "sample message to read")
                Future.successful(Redirect(s"http://localhost:9001/getProductList?user=$userName").withSession("user" -> userName).flashing("success" -> "User authenticated successfully!"))
              }.recover {
                case ex: Exception =>
                  println(s"An error occurred: $ex")
                  Redirect (routes.LoginController.index).flashing("error" -> "An error occurred during login")
              }
            }
          case _ =>
            Future.successful(Redirect(routes.LoginController.index).flashing("error" -> "Invalid email or password"))
        }.recover {
          case e: Exception =>
            Redirect(routes.LoginController.index).flashing("error" -> "Error occurred during authentication")
        }
      case _ =>
        Future.successful(Redirect(routes.LoginController.index).flashing("error" -> "Missing email or password"))
    }
  }

  private def idGenerator(): String = {
    val uuid = UUID.randomUUID()
    val shortId = uuid.toString.take(8)  // Take the first 8 characters
    shortId
  }

  def userCreation: Action[AnyContent] = Action.async { implicit request =>
    request.body.asFormUrlEncoded match {
      case Some(args) =>
       val id = idGenerator()
       val name = args("name").head
       val email = args("email").head
       val password = args("pass").head
       val location = args("loc").head
      userRepository.add(id, name, email, password, location).map { _ =>
         Redirect(routes.LoginController.index).flashing("success" -> "User created successfully!")
       }.recover {
         case e: Exception =>
           Redirect(routes.LoginController.index).flashing("error" -> s"Error occurred: ${e.getMessage}")
       }
       case None =>
         Future.successful(Redirect(routes.LoginController.index).flashing("error" -> "Invalid form submission"))
     }
  }

  def signUpPage: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.signup())
  }
}
