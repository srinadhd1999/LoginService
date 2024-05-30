package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

case class User(id: String, name: String, email: String, password: String, location: String)

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class UserTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name")

    def email = column[String]("email")

    def password = column[String]("password")

    def location = column[String]("location")

    def * = (id, name, email, password, location) <> ((User.apply _).tupled, User.unapply)
  }

  val users = TableQuery[UserTable]

  def list(): Future[Seq[User]] = db.run(users.result)

  def find(id: String): Future[Option[User]] = db.run(users.filter(_.id === id).result.headOption)

  def add(id: String, name: String, email: String, password: String, location: String): Future[User] = db.run {
    (users += User(id, name, email, password, location)).map(_ => (User(id, name, email, password, location)))
  }

  def update(user: User): Future[Int] = db.run(users.filter(_.id === user.id).update(user))

  def authenticate(email: String, password: String): Future[Option[User]] = db.run {
    users.filter(user => user.email === email && user.password === password).result.headOption
  }

  def getUserName(email: String): Future[String] = {
    db.run(users.filter(_.email === email).map(_.name).result.headOption).flatMap {
      case Some(username) => Future.successful(username)
      case None => Future.failed(new Exception(s"No user found with email: $email"))
    }
  }
}