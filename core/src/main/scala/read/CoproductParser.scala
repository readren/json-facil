package read

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import read.CoproductParserHelper.{Coproduct, CphProductInfo}
import read.SyntaxParsers.{string, _}

object CoproductParser {
	private sealed trait Field[+V] {
		def name: String
		def value: V;
		@inline def isDefined: Boolean = name != null;
	}
	private case class DefinedField[+V](name: String, value: V) extends Field[V];
	private object UndefinedField extends Field[Nothing] {
		override def name: String = null.asInstanceOf[String]
		override def value: Nothing = throw new NoSuchElementException
	}

	private val fieldNameParser: Parser[String] = string <~ skipSpaces <~ colon <~ skipSpaces

	private def definedFieldsNamesIn(fields: Iterable[Field[Any]]): String = fields.collect { case DefinedField(fieldName, _) => fieldName } mkString ", ";

}


class CoproductParser[C <: Coproduct](helper: CoproductParserHelper[C]) extends Parser[C] {
	import CoproductParser._
	import Parser._

	assert(helper != null); // Fails here when the macro expansion of CoproductParserHelper fails for some reason. Usually because a compilation error of the expanded code. To find the place in the log search the string "<empty>"

	/** Used by this parser to maintains the state of a product and known it's [[CphProductInfo]]. */
	private class Manager(val productInfo: CphProductInfo[C]) {
		var missingRequiredFieldsCounter: Int = productInfo.numberOfRequiredFields;
		var isViable: Boolean = true;
	}

	/** An [[ArrayBuffer]] builder that discards [[UndefinedField]]s */
	private class DefinedFieldsArrayBuilder extends mutable.GrowableBuilder[Field[Any], ArrayBuffer[Field[Any]]](new ArrayBuffer(helper.fieldsInfo.size)) {
		override def addOne(field: Field[Any]): this.type = {
			if (field.isDefined) {
				elems += field
			}
			this
		}
	}

	private def fieldParser(managers: ArraySeq[Manager]): Parser[Field[Any]] = fieldNameParser >> { fieldName =>
		if (fieldName == helper.discriminator) {
			PrimitiveParsers.jpString.map { productName =>
				// as side effect, actualize the product's managers
				managers.foreach { m =>
					if (m.productInfo.name != productName)
						m.isViable = false;
				}
				UndefinedField
			}
		} else {
			helper.fieldsInfo.get(fieldName) match { // TODO optimise
				case Some(fieldValueParser) =>
					// as side effect, actualize the product's managers
					managers.foreach { manager =>
						val index = manager.productInfo.fields.indexWhere(_.name == fieldName);
						if(index < 0) {
							manager.isViable = false;
						} else if (manager.productInfo.fields(index).oDefaultValue.isEmpty) {
							manager.missingRequiredFieldsCounter -= 1
						}
					}
					// parse the fiel value
					fieldValueParser.map(DefinedField(fieldName, _));

				case None =>
					skipJsValue.^^^(UndefinedField);
			}
		}
	}

	private def productParser(managers: ArraySeq[Manager]): Parser[C] = {
		'{' ~> skipSpaces ~> (fieldParser(managers) <~ skipSpaces).rep1SepGen[Pos, ArrayBuffer[Field[Any]]](coma ~> skipSpaces, () => new DefinedFieldsArrayBuilder) <~ '}' >> { parsedFields =>

			var chosenManager: Manager = null;
			var isAmbiguous: Boolean = false;
			managers.foreach { manager =>
				if (manager.isViable && manager.missingRequiredFieldsCounter == 0) {
					if (chosenManager != null) {
						isAmbiguous = true;
					} else {
						chosenManager = manager;
					}
				}
			}
			if (chosenManager == null) {
				fail[C](s"There is no product extending ${helper.fullName} with all the fields contained in the json object being parsed. The contained fields are: ${definedFieldsNamesIn(parsedFields)}. Note that only the fields that are defined in at least one of said products are considered.")
			} else if (isAmbiguous) {
				fail[C](s"""Ambiguous products: more than one product of the coproduct "${helper.fullName}" has the fields contained in the json object being parsed. The contained fields are: ${definedFieldsNamesIn(parsedFields)}; and the viable products are: ${managers.iterator.filter(m => m.isViable && m.missingRequiredFieldsCounter == 0).map(_.productInfo.name).mkString(", ")}.""");
			} else {
				// build the product constructor's arguments list
				val chosenProductFields = chosenManager.productInfo.fields;
				val ctorArgs: Array[Any] = new Array(chosenProductFields.size);
				var argIndex: Int = 0;
				while (argIndex < chosenProductFields.size) {
					val fieldInfo = chosenProductFields(argIndex);
					val matchingParsedFieldIndex = parsedFields.indexWhere(_.name == fieldInfo.name);
					ctorArgs(argIndex) = if (matchingParsedFieldIndex >= 0) {
						parsedFields(matchingParsedFieldIndex).value;
					} else {
						fieldInfo.oDefaultValue.get;
					}
					argIndex += 1;
				}
				hit(chosenManager.productInfo.constructor(ArraySeq.unsafeWrapArray(ctorArgs)));

			}
		}
	}

	override def parse(cursor: Cursor): C = {
		val managers = helper.productsInfo.map(new Manager(_));
		val c = productParser(managers).parse(cursor)
		if (cursor.missed) {
			cursor.fail(s"Invalid json object format found while parsing an instance of ${helper.fullName}");
		}
		c
	}


}
