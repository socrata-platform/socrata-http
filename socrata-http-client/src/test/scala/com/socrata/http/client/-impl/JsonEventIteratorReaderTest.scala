package com.socrata.http.client.`-impl`

import org.scalatest.{FunSuite, MustMatchers}
import org.scalatest.prop.PropertyChecks
import java.io.Reader
import com.rojoma.json.ast.JValue
import com.rojoma.json.testsupport.ArbitraryJValue
import com.rojoma.json.io.{CompactJsonWriter, JValueEventIterator}
import org.scalacheck.Arbitrary

class JsonEventIteratorReaderTest extends FunSuite with MustMatchers with PropertyChecks {
  def readAll(r: Reader, blockSize: Int) = {
    val buf = new Array[Char](blockSize)
    val sb = new StringBuilder
    def loop() {
      r.read(buf) match {
        case -1 => // done
        case n => sb.appendAll(buf, 0, n); loop()
      }
    }
    loop()
    sb.toString
  }

  test("A JsonEventReader produces the same characters as a CompactJsonWriter") {
    forAll(ArbitraryJValue.ArbitraryJValue.arbitrary, implicitly[Arbitrary[Short]].arbitrary) { (x: JValue, blockSize: Short) =>
      whenever(blockSize != 0) {
        readAll(new JsonEventIteratorReader(JValueEventIterator(x)), blockSize & 0xffff) must equal (CompactJsonWriter.toString(x))
      }
    }
  }
}
