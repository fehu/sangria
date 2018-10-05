package sangria.macros.derive

import org.scalatest.{ Matchers, WordSpec }
import sangria.util.FutureResultSupport
import sangria.macros._
import sangria.execution.Executor
import sangria.schema._
import sangria.streaming.future._
import spray.json.{ JsArray, JsNumber, JsObject }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeriveSubscriptionMacroSpec extends WordSpec with Matchers with FutureResultSupport {

  private val data0 = 0 until 100
  case class DataSource(data: Stream[Int] = data0.toStream) {
    def multiplyFuture1(n: Int): Future[List[Long]] = Future { data.map(_.toLong * n).toList }
    def multiplyFuture2(n: Int): Future[List[Long]] = multiplyFuture1(n)
  }

  // == == == == == == == == == TODO == == == == == == == == ==

  "ObjectType subscription derivation" should {
    "work with future" in {
      val tpe = deriveContextObjectType[DataSource, DataSource, Unit](
        locally,
        ExcludeFields("data"),
        IncludeMethods("multiplyFuture1", "multiplyFuture2"),
        Subscription[Future, List[Long]]("multiplyFuture1"),
        Subscription[Future, List[Long]]("multiplyFuture2", _.map(Action[DataSource, List[Long]](_)))
      )

      val stub = ObjectType[DataSource, Unit]("stub", fields[DataSource, Unit](Field("stub", BooleanType, resolve = _ => true)))
      val schema = Schema(stub, subscription = Some(tpe))
      val query =
        graphql"""
          subscription Test {
            multiplyFuture1(n: 2)
          }
        """

      import sangria.marshalling.sprayJson._

      Executor.execute(schema, query, DataSource()).await should be (
        JsObject("data" → JsObject("multiplyFuture1" -> JsArray(data0.map(_ * 2).map(JsNumber(_)).toVector))))
    }
  }
}
