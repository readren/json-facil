package jsfacile.test

import java.nio.charset.Charset

import SampleADT.{PresentationData, presentationDataOriginal}
import ParserMacrosTest.presentationDataFormat

object SpeedTest {

	def main(args: Array[String]): Unit = {

		var totalJsFacileDuration: Float = 0;
		var totalJsFacileNewDuration: Float = 0;
		var totalSprayDuration: Float = 0;
		var totalJsoniterDuration: Float = 0;
		for (j <- 0 to 10) {
			println("----");

			System.gc();
			val jsFacileDuration = {
				import jsfacile.api._

				//				BasicParsers.newVersion = false;
				if(j==0) println("jsfacile:\t\t" + presentationDataOriginal.toJson.value)
				val presentationDataJson = presentationDataOriginal.toJson.value.toCharArray
				val ppd = parserOf[PresentationData]

				val start = java.lang.System.nanoTime();
				for (i <- 0 to 1000_000) {
					val cursor = new CursorStr(presentationDataJson)
					ppd.parse(cursor);
				}
				val duration = (java.lang.System.nanoTime() - start) / 1000000000f;
				println("jsfacile:\t\t" + duration);
				if (j > 0) totalJsFacileDuration += duration
				duration
			}

			if (false) {
				System.gc();
				val jsFacileNewDuration = {
					import jsfacile.api._

					//					BasicParsers.newVersion = true;
					val presentationDataJson = presentationDataOriginal.toJson.value.toCharArray
					val ppd = parserOf[PresentationData]

					val start = java.lang.System.nanoTime();
					for (i <- 0 to 1000_000) {
						val cursor = new CursorStr(presentationDataJson)
						ppd.parse(cursor);
					}
					val duration = (java.lang.System.nanoTime() - start) / 1000000000f;
					println("jsfacile new:\t" + duration);
					if (j > 0) totalJsFacileNewDuration += duration
					duration
				}
				val differenceVsOld = 100 * (jsFacileDuration - jsFacileNewDuration) / jsFacileNewDuration
				println(s"difference vs old: $differenceVsOld%");

			} else if(true) {

				System.gc();
				val sprayDuration = {
					import spray.json.{enrichAny, enrichString}

					val presentationDataJson = presentationDataOriginal.toJson.compactPrint
					if(j==0) println("spray:\t\t\t" + presentationDataJson)
					val start = java.lang.System.nanoTime();
					for (i <- 0 to 1000_000) {
						presentationDataFormat.read(presentationDataJson.parseJson);
					}
					val duration = (java.lang.System.nanoTime() - start) / 1000000000f;
					println("spray:\t\t\t" + duration);
					if (j > 0) totalSprayDuration += duration;
					duration
				}
				val differenceVsSpray = 100 * (sprayDuration - jsFacileDuration) / jsFacileDuration
				println(s"difference vs Spray: $differenceVsSpray%");

				System.gc();
				val jsoniterDuration = {
					import com.github.plokhotnyuk.jsoniter_scala.macros._
					import com.github.plokhotnyuk.jsoniter_scala.core._

					implicit val codec: JsonValueCodec[PresentationData] = JsonCodecMaker.make

					val presentationDataJson0 = writeToString(presentationDataOriginal)
					if(j==0) println("jsoniter:\t\t" + presentationDataJson0)
					val presentationDataJson1 = presentationDataJson0.getBytes(Charset.forName("UTF8"));
					val start = java.lang.System.nanoTime();
					for (i <- 0 to 1000_000) {
						try {
							readFromArray(presentationDataJson1)
						} catch {
							case e:  Exception => e.printStackTrace()
						}
					}
					val duration = (java.lang.System.nanoTime() - start) / 1000000000f;
					println("jsoniter:\t\t" + duration);
					if (j > 0) totalJsoniterDuration += duration;
					duration
				}
				val differenceVsJsoniter = 100 * (jsoniterDuration - jsFacileDuration) / jsFacileDuration
				println(s"difference vs Jsoniter: $differenceVsJsoniter%");
			}
		}

		println("-------------")
		println(s"total jsFacile duration old	: $totalJsFacileDuration")
		println(s"total jsFacile duration new	: $totalJsFacileNewDuration")
		println(s"total spray duration			: $totalSprayDuration")
		println(s"total jsoniter duration		: $totalJsoniterDuration")
		println(s"total difference vs Spray		: jsfacile is ${100 * (totalSprayDuration - totalJsFacileDuration) / totalJsFacileDuration}% faster than spray")
		println(s"total difference vs Jsoniter	: jsfacile is ${100 * (totalJsoniterDuration - totalJsFacileDuration) / totalJsFacileDuration}% faster than jsoniter")
	}
}
