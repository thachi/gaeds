import org.scalatest.{ WordSpec, BeforeAndAfter }
import org.scalatest.matchers.MustMatchers

import java.util.Date

import scala.collection.JavaConverters._

import com.google.appengine.api.blobstore.BlobKey
import com.google.appengine.api.datastore._
import com.google.appengine.api.datastore.FetchOptions.Builder._
import com.google.appengine.api.users.User
import com.google.appengine.tools.development.testing.{ LocalDatastoreServiceTestConfig, LocalServiceTestHelper }

import com.github.hexx.gaeds._
import com.github.hexx.gaeds.Property._

object Util {
  def stringToByteArray(s: String) = s.toArray.map(_.toByte)
  def createShortBlob(s: String) = new ShortBlob(stringToByteArray(s))
  def createBlob(s: String) = new Blob(stringToByteArray(s))
}

class Data(
    val boolean: Property[Boolean],
    val shortBlob: Property[ShortBlob],
    val blob: Property[Blob],
    val category: Property[Category],
    val date: Property[Date],
    val email: Property[Email],
    val float: Property[Float],
    val double: Property[Double],
    val geoPt: Property[GeoPt],
    val user: Property[User],
    val short: Property[Short],
    val int: Property[Int],
    val long: Property[Long],
    val blobKey: Property[BlobKey],
    val keyValue: Property[Key],
    val link: Property[Link],
    val imHandle: Property[IMHandle],
    val postalAddress: Property[PostalAddress],
    val rating: Property[Rating],
    val phoneNumber: Property[PhoneNumber],
    val string: Property[String],
    val text: Property[Text])
  extends Mapper[Data] {
  def this() =
    this(
      false,
      Util.createShortBlob(""),
      Util.createBlob(""),
      new Category(""),
      new Date,
      new Email(""),
      0,
      0,
      new GeoPt(0F, 0F),
      new User("", ""),
      0.toShort,
      0,
      0L,
      new BlobKey(""),
      KeyFactory.createKey("default", 1L),
      new Link(""),
      new IMHandle(IMHandle.Scheme.unknown, ""),
      new PostalAddress(""),
      new Rating(0),
      new PhoneNumber(""),
      "",
      new Text(""))
}

object Data extends Data

// low-level sample
import com.google.appengine.api.datastore.{ DatastoreServiceFactory, Entity }
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.FilterOperator._
import com.google.appengine.api.datastore.Query.SortDirection._

case class Person(name: String, age: Long)

// gaeds sample
import com.github.hexx.gaeds._
import com.github.hexx.gaeds.Property._

class Person2(val name: Property[String], val age: Property[Int]) extends Mapper[Person2] {
  def this() = this("", 0)
  override def toString() = "Person(" + name + "," + age + ")"
}
object Person2 extends Person2

class GAEDSSpec extends WordSpec with BeforeAndAfter with MustMatchers {
  def data =
    new Data(
      true,
      Util.createShortBlob("shortBlob"),
      Util.createBlob("blob"),
      new Category("category"),
      new Date,
      new Email("email"),
      1.23F,
      1.23,
      new GeoPt(1.23F, 1.23F),
      new User("test@gmail.com", "gmail.com"),
      123.toShort,
      123,
      123L,
      new BlobKey("blobKey"),
      KeyFactory.createKey("data", 2L),
      new Link("http://www.google.com/"),
      new IMHandle(IMHandle.Scheme.sip, "imHandle"),
      new PostalAddress("postalAddress"),
      new Rating(1),
      new PhoneNumber("0"),
      "string",
      new Text("text"))

  val helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
  before {
    helper.setUp()
  }
  after {
    helper.tearDown()
  }

  def putTest(k: Key, d: Data) = {
    k.getId must not be 0
    d.key.get must be === k
  }
  def putAndGetTest(k: Key, d1: Data, d2: Data) = {
    putTest(k, d1)
    d1 must be === d2
    d1.key.get must be === d2.key.get
    d1 must be === d2
  }

  def printKey(k: Key) {
    println(k)
    println(k.getId)
    println(k.getKind)
    println(k.getName)
    println(k.getNamespace)
    println(k.isComplete)
  }

  "Entity" should {
    "put and get" in {
      val d1 = data
      val k = d1.put
      val d2 = Data.get(k)
      putAndGetTest(k, d1, d2)
      Datastore.delete(k)
    }
    "multi-put and multi-get" in {
      val ds1 = Seq(data, data, data)
      val ks = Datastore.put(ds1:_*)
      val ds2 = Data.get(ks:_*)
      for (((k, d1), d2) <- ks zip ds1 zip ds2) {
        putAndGetTest(k, d1, d2)
      }
      Datastore.delete(ks:_*)
    }
    "low-level api sample" in {
      val ds = DatastoreServiceFactory.getDatastoreService
      // 保存
      val p = Person("John", 13)
      val e = new Entity("Person")
      e.setProperty("name", p.name)
      e.setProperty("age", p.age)
      val key = ds.put(e)

      // 取得
      val e2 = ds.get(key)
      val p2 = Person(e2.getProperty("name").asInstanceOf[String], e2.getProperty("age").asInstanceOf[Long])
    }
    "gaeds sample" in {
      // 保存
      val p = new Person2("John", 13)
      val key = p.put()

      // 取得
      val p2 = Person2.get(key)
    }
  }
  "Query" should {
    "basic" in {
      val d1 = data
      val k = d1.put
      val d2 = Data.query.asIterator.next
      Data.query.count must be === 1
      Datastore.delete(k)
      d1 must be === d2
    }
    "QueryResult" in {
      val ds = Seq(data, data, data)
      Datastore.put(ds:_*)
      val ite1 = Data.query.asQueryResultIterator(false)
      val ite2 = Data.query.asIteratorWithCursorAndIndex
      for ((e, (d, c, i)) <- ite1.asScala zip ite2) {
        ite1.getCursor must be === c()
        Data.fromEntity(e) must be === d
      }
    }
    "low-level api sample" in {
      val ds = DatastoreServiceFactory.getDatastoreService
      val q = new Query("Person")
      q.addFilter("age", GREATER_THAN_OR_EQUAL, 10)
      q.addFilter("age", LESS_THAN_OR_EQUAL, 20)
      q.addSort("age", ASCENDING)
      q.addSort("name", ASCENDING)
      val ps = for (e <- ds.prepare(q).asIterator.asScala) yield {
        Person(e.getProperty("name").asInstanceOf[String], e.getProperty("age").asInstanceOf[Long])
      }
    }
    "gaeds sample" in {
    }
  }
}
