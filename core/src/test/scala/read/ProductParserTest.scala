package read


import org.scalatest.matchers.should.Matchers
import org.scalatest.refspec.RefSpec
import read.CoproductParserHelper.Coproduct
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonWriter, RootJsonFormat}

//noinspection TypeAnnotation
object ProductParserTest extends PrimitiveParsers with DefaultJsonProtocol {

	case class Simple(text: String, number: Long)
	case class Nest(name: String, simple: Simple)
	case class Tree(height: Int, nests: List[Nest], mapa: Map[String, Simple])

	object DistanceUnit extends Enumeration {
		type DistanceUnit = Value
		val Meter, Millimeter = Value;
	}

	case class Distance(value: Double, unit: DistanceUnit.Value)

	sealed trait Shape extends Coproduct
	case class Box(axis: List[Distance]) extends Shape
	case class Sphere(radius: Distance) extends Shape

	trait Thing extends Coproduct {
		def enclosingShape: Shape
		def description: String
	}
	case class Table(enclosingShape: Shape, legsAmount: Int, description: String) extends Thing
	case class Shelf(enclosingShape: Shape, levelsAmount: Int, description: String) extends Thing
	case class Ball(enclosingShape: Shape, description: String) extends Thing

	type Price = BigDecimal
	type ThingId = String
	type Catalog = Map[ThingId, Price]
	type Inventory = Map[ThingId, Int]
	case class PresentationData(catalog: Catalog, inventory: Inventory, things: Map[ThingId, Thing])

	// ------------------------------- //

	implicit val simpleFormat = jsonFormat2(Simple)
	implicit val anidadoFormat = jsonFormat2(Nest)
	implicit val treeFormat = jsonFormat3(Tree)

	class EnumJsonConverter[T <: scala.Enumeration](enu: T) extends RootJsonFormat[T#Value] {
		override def write(obj: T#Value): JsValue = JsString(obj.toString)

		override def read(json: JsValue): T#Value = {
			json match {
				case JsString(txt) => enu.withName(txt)
				case somethingElse => throw DeserializationException(s"Expected a value from enum $enu instead of $somethingElse")
			}
		}
	}

	implicit val distanceUnitFormat = new EnumJsonConverter(DistanceUnit)
	implicit val distanceFormat = jsonFormat2(Distance)
	implicit val boxFormat = jsonFormat1(Box)
	implicit val sphereFormat = jsonFormat1(Sphere);
	implicit val shapeFormat = new RootJsonFormat[Shape] {
		override def read(json: JsValue): Shape = ???
		override def write(obj: Shape): JsValue = obj match {
			case b: Box => boxFormat.write(b)
			case s: Sphere => sphereFormat.write(s);
		}
	}
	implicit val tableFormat = jsonFormat3(Table)
	implicit val shelfFormat = jsonFormat3(Shelf)
	implicit val ballFormat = jsonFormat2(Ball)
	implicit val thingFormat = new RootJsonFormat[Thing] {
		override def read(json: JsValue): Thing = ???
		override def write(obj: Thing): JsValue = obj match {
			case t: Table => tableFormat.write(t)
			case s: Shelf => shelfFormat.write(s);
			case b: Ball => ballFormat.write(b);
		}
	}
	implicit val presentationDataFormat = jsonFormat3(PresentationData)

	//////////////

	val simpleOriginal = Simple("hola", 5L)
	val simpleJson = simpleOriginal.toJson.prettyPrint
	val nestOriginal = Nest("chau", Simple("hola", 5L))
	val nestJson = nestOriginal.toJson.prettyPrint
	val treeOriginal = Tree(7, List(nestOriginal), Map("clave" -> simpleOriginal))
	val treeJson = treeOriginal.toJson.prettyPrint;

	val tableA = "table_A" -> Table(legsAmount = 4, description = "dinner room", enclosingShape = Box(List(Distance(1.5, DistanceUnit.Meter), Distance(2, DistanceUnit.Meter), Distance(750, DistanceUnit.Millimeter))));
	val shelfA = "shelf_A" -> Shelf(levelsAmount = 4, description = "for books", enclosingShape = Box(List(Distance(2.5, DistanceUnit.Meter), Distance(2, DistanceUnit.Meter), Distance(500, DistanceUnit.Millimeter))));
	val ballA = "ball_A" -> Ball(description = "soccer", enclosingShape = Sphere(radius = Distance(20, DistanceUnit.Millimeter)));
	val catalog = Map("table_A" -> BigDecimal(123.4), "shelf_A" -> BigDecimal(32.1))
	val inventory = Map("table_A" -> 4, "shelf_A" -> 3, "ball_A" -> 8)
	val presentationDataOriginal = PresentationData(catalog, inventory, Map(tableA, shelfA, ballA))
	val presentationDataJson = presentationDataOriginal.toJson.prettyPrint

}


//noinspection TypeAnnotation
class ProductParserTest extends RefSpec with Matchers  { // with ScalaCheckDrivenPropertyChecks with JsonGen {
	import ProductParserTest._
	import ProductParser.jpProduct
	import CoproductParser.jpCoproduct
	import IterableParser.iterableParser
	import MapParser.unsortedMapParser
	import MapParser.sortedMapParser
	import NonVariantHolderOfAMapFactory._
	import NonVariantHolderOfASortedMapFactory._

//	private val universe: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe

	object `Given sample ADTs...` {

		def `Implicit resolution of the interpreters should work`(): Unit = {
			//	import universe._
//			val rs = reify(implicitly[lector.GuiaLectorProducto[Simple]])

			val simpleHelper = ProductParserHelper.materializeHelper[Simple];
			assert(simpleHelper != null && simpleHelper.fieldsInfo.nonEmpty && simpleHelper.fieldsInfo.forall(_._2.valueParser != null))

			val productParser = new ProductParser[Simple](simpleHelper)
			assert(productParser != null && productParser.parse(new CursorStr(simpleJson)) == simpleOriginal)

			val simpleParser = Parser.apply[Simple]
			assert(simpleParser.isInstanceOf[ProductParser[Simple]])
		}

		def `Json interpretation should work for a simple product`(): Unit = {
			val cursor = new CursorStr(simpleJson)
			val simpleParser = Parser.apply[Simple]
			val simpleParsed = simpleParser.parse(cursor)
			assert(simpleParsed == simpleOriginal)
		}

		def `Json interpretation should work for nested products`(): Unit = {
			val cursor = new CursorStr(nestJson)
			val nestParser = Parser.apply[Nest]
			val nestParsed = nestParser.parse(cursor)
			assert(nestParsed == nestOriginal)
		}

		def `Json interpretation should work for products with iterables`(): Unit = {
			val cursor = new CursorStr(treeJson)
			val treeParser = Parser.apply[Tree]
			val treeParsed = treeParser.parse(cursor)
			assert(treeParsed == treeOriginal)
		}

		def `Json interpretation should work fo simple ADTs with a coproduct`(): Unit = {
			var cursor = new CursorStr(tableA._2.toJson.prettyPrint)
			val tableParser = Parser.apply[Table]
			val tableAParsed = tableParser.parse(cursor)
			assert(tableAParsed == tableA._2)

			cursor = new CursorStr(ballA._2.toJson.prettyPrint)
			val ballParser = Parser.apply[Ball]
			val ballParsed = ballParser.parse(cursor)
			assert(ballParsed == ballA._2)

			cursor = new CursorStr(shelfA._2.toJson.prettyPrint)
			val shelfParser = Parser.apply[Shelf]
			val shelfAParsed = shelfParser.parse(cursor)
			assert(shelfAParsed == shelfA._2)
		}

		def `Json interpretation should work for complex ADTs`(): Unit = {
			val cursor = new CursorStr(presentationDataJson)
			val presentationDataParser = Parser.apply[PresentationData]
			val presentationDataParsed = presentationDataParser.parse(cursor)
			assert(presentationDataParsed == presentationDataOriginal)
		}
	}

}
